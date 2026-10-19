package com.newoether.agora.notes
import android.app.Application
import android.content.pm.ActivityInfo
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.api.openai.DeepSeekProvider
import com.newoether.agora.data.SkillManager
import com.newoether.agora.data.local.MessageEntity
import com.newoether.agora.viewmodel.projectProviderMessages
import com.newoether.agora.model.*
import com.newoether.agora.tool.*
import com.newoether.agora.viewmodel.GenerationContext
import com.newoether.agora.viewmodel.MessagePayloadBuilder
import com.newoether.agora.util.DebugLog
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real stores/providers/codecs with loopback transport; no substitute tool implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesHostWorkflowTest {
 private val app get()=RuntimeEnvironment.getApplication()
 private val ctx=GenerationContext(conversationId="host",notesIntegrationEnabled=true,skillReadAccess=true,skillModifyAccess=true)
 @Before fun setup(){
  shadowOf(app.packageManager).installPackage(android.content.pm.PackageInfo().apply {
   packageName="com.kalamos.notebook"
   applicationInfo=android.content.pm.ApplicationInfo().apply{packageName="com.kalamos.notebook"}
   activities=arrayOf(ActivityInfo().apply{packageName="com.kalamos.notebook";name="com.kalamos.notebook.exchange.AgoraDocumentActivity";applicationInfo=android.content.pm.ApplicationInfo().apply{packageName="com.kalamos.notebook"}})
  })
  DebugLog.forceEnabled=false;DebugLog.init(app)
 }
 @Test fun notesSchemasHaveDistinctResponsibilities(){
  val tools=NotesProposalToolProvider(app).definitions(ctx)
  assertEquals(setOf(NotesProposalToolProvider.NAME,NotesProposalToolProvider.TRANSCRIBE_NAME),tools.map{it.function.name}.toSet())
  val organize=tools.single{it.function.name==NotesProposalToolProvider.NAME}.function
  assertFalse(organize.parameters.properties.containsKey("operation"))
  assertTrue(organize.description.contains("For handwriting recognition call replace_notes_handwriting instead"))
  val recognition=tools.single{it.function.name==NotesProposalToolProvider.TRANSCRIBE_NAME}.function
  assertTrue(recognition.parameters.properties.containsKey("deferToFormatting"))
  assertFalse(organize.parameters.properties.containsKey("deferToFormatting"))
 }
 @Test fun realSkillStoreCoversCatalogCreateReadPatchRenameDescriptionDeleteAndAccess(){
  val manager=SkillManager(app);val provider=SkillToolProvider(manager)
  fun call(name:String,args:JsonObject,context:GenerationContext=ctx)=runBlocking { provider.executeOneShotResult(name,args.toString(),context) }
  val filename="notes-"+UUID.randomUUID()+".md"
  assertFalse(call("create_skill_file",buildJsonObject { put("name",filename);put("content","Literal transcription. Never invent tasks.");put("description","Notes workflow") }).isError)
  assertFalse(manager.catalog().contains("Notes workflow"));assertFalse(manager.catalog().contains("Never invent tasks"))
  val frozen=manager.catalog()
  assertTrue(call("read_skill_file",buildJsonObject{put("name",filename)}).text.endsWith("Literal transcription. Never invent tasks."))
  call("edit_skill_file",buildJsonObject{put("name",filename);put("operation","patch");put("old_string","tasks");put("new_string","deadlines")})
  assertTrue(call("read_skill_file",buildJsonObject{put("name",filename)}).text.contains("deadlines"))
  assertTrue(frozen.contains(filename));assertFalse(frozen.contains("Notes workflow"))
  assertTrue(call("read_skill_file",buildJsonObject{put("name",filename)},ctx.copy(skillReadAccess=false)).isError)
  call("edit_skill_file",buildJsonObject{put("name",filename);put("operation","rename");put("new_name","renamed.md")})
  call("edit_skill_file",buildJsonObject{put("name","renamed.md");put("operation","describe");put("description","Updated workflow")})
  assertFalse(manager.catalog().contains("Updated workflow"))
  call("delete_skill_file",buildJsonObject{put("name","renamed.md")})
  assertFalse(manager.listFiles().any{it.name=="renamed.md"})
 }
 @Test fun selectionPrototypeExpandsExactSourcesAndRejectsStaleUnknownAndRepeatedLabels():Unit=runBlocking {
  val docId=UUID.randomUUID().toString();val revision="b".repeat(64)
  val bounds=buildJsonObject{put("x",10);put("y",20);put("width",100);put("height",40)}
  val regions=listOf("one","two").map{NotesInkRegion("ink-"+it,"first",it,bounds,listOf("stroke-"+it))}
  val document=NotesDocument("kalamos.notes-document/v1",docId,revision,"Selection test",emptyList(),listOf(NotesPage("first","pages/first.md","pages/first.json")),emptyMap(),inkRegions=regions)
  val binding=buildJsonObject {
   put("documentId",docId);put("baseRevisionId",revision);putJsonArray("targetPageIds"){add("first")}
   putJsonArray("selections"){add(buildJsonObject{put("id","L1");put("pageId","first");put("bounds",bounds);putJsonArray("sourceInkRegionIds"){regions.forEach{add(it.id)}}})}
  }
  val experiment=NotesSelectionExperiment(NotesProposalToolProvider(app),binding)
  val context=experiment.context(document)
  assertFalse(context.contains("ink-one"));assertTrue(context.contains("L1"))
  val input=buildJsonObject {
   put("documentId",docId);put("baseRevisionId",revision);putJsonArray("targetPageIds"){add("first")};put("title","Selection test")
   putJsonArray("blocks"){add(buildJsonObject{put("type","task");put("text","Buy milk");put("checked",false);putJsonArray("sourceSelectionIds"){add("L1")}})}
  }
  val result=experiment.execute(NotesProposalToolProvider.TRANSCRIBE_NAME,input.toString(),ctx)
  assertEquals(listOf("ink-one","ink-two"),NotesDocumentFormat.decodeProposal(result.structuredContent!!).blocks.single().sourceInkRegionIds)
  suspend fun rejected(value:JsonObject){
   var failed=false
   try {experiment.execute(NotesProposalToolProvider.TRANSCRIBE_NAME,value.toString(),ctx)}catch(_:IllegalArgumentException){failed=true}
   assertTrue("Invalid selection input must not reach the real Notes tool",failed)
  }
  rejected(JsonObject(input+("baseRevisionId" to JsonPrimitive("c".repeat(64)))))
  rejected(JsonObject(input+("blocks" to JsonArray(input.getValue("blocks").jsonArray+input.getValue("blocks").jsonArray))))
  rejected(Json.parseToJsonElement(input.toString().replace("L1","unknown")).jsonObject)
  assertThrows(IllegalArgumentException::class.java){experiment.context(document.copy(revisionId="c".repeat(64)))}
 }
 @Test fun stagedRecognitionFormatsOnceAcrossProviderRecreationWithoutIntermediateApply():Unit=runBlocking {
  val owner=ctx.copy(conversationId="staging-"+UUID.randomUUID())
  val docId=UUID.randomUUID().toString()
  val input=buildJsonObject {
   put("documentId",docId);put("baseRevisionId","b".repeat(64));putJsonArray("targetPageIds"){add("first")}
   put("title","To do");put("deferToFormatting",true)
   putJsonArray("blocks"){
    add(buildJsonObject{put("id","buy");put("type","paragraph");put("text","Buy milk");putJsonArray("sourceInkRegionIds"){add("ink-one")}})
    add(buildJsonObject{put("id","eggs");put("type","paragraph");put("text","eggs");putJsonArray("sourceInkRegionIds"){add("ink-two")}})
   }
  }
  val staged=NotesProposalToolProvider(app).executeOneShotResult(NotesProposalToolProvider.TRANSCRIBE_NAME,input.toString(),owner)
  assertNull(staged.structuredContent)
  assertFalse(staged.text.contains("ink-one"))
  val recognition=Json.parseToJsonElement(staged.text.substringAfter('\n')).jsonObject
  val format=buildJsonObject {
   put("recognitionProposalId",recognition.getValue("recognitionProposalId"))
   put("documentId",docId);put("baseRevisionId","b".repeat(64));putJsonArray("targetPageIds"){add("first")};put("title","To do")
   putJsonArray("blocks"){
    add(buildJsonObject{put("id","buy");put("type","task");put("text","Buy milk");put("checked",false)})
    add(buildJsonObject{put("id","eggs");put("type","bullet");put("text","eggs");put("parentId","buy")})
   }
  }
  val provider=NotesProposalToolProvider(app)
  assertThrows(IllegalArgumentException::class.java){runBlocking{
   provider.executeOneShotResult(NotesProposalToolProvider.NAME,format.toString(),owner.copy(conversationId="another"))
  }}
  val result=provider.executeOneShotResult(NotesProposalToolProvider.NAME,format.toString(),owner)
  val proposal=NotesDocumentFormat.decodeProposal(result.structuredContent!!)
  assertEquals("replace_ink",proposal.operation)
  val retry=provider.executeOneShotResult(NotesProposalToolProvider.NAME,JsonObject(format+("operation" to JsonPrimitive("replace_ink"))).toString(),owner)
  assertEquals(proposal.proposalId,NotesDocumentFormat.decodeProposal(retry.structuredContent!!).proposalId)
  val minimal=JsonObject(format.filterKeys { it in setOf("recognitionProposalId","blocks") })
  val compact=NotesDocumentFormat.decodeProposal(provider.executeOneShotResult(NotesProposalToolProvider.NAME,minimal.toString(),owner).structuredContent!!)
  assertEquals(proposal,compact)
  assertFalse(staged.text.contains(docId));assertFalse(staged.text.contains("b".repeat(64)))
  assertEquals(listOf("ink-one"),proposal.blocks[0].sourceInkRegionIds)
  assertEquals("buy",proposal.blocks[1].parentId)
  val tracker=NotesAutomaticReturn()
  tracker.update(emptyList(),false);tracker.update(emptyList(),true)
  val segments=listOf(staged,result).map { execution -> MessageSegment(type="tool",toolName=NotesProposalToolProvider.NAME,
   toolState=ToolExecutionStates.SUCCEEDED,toolStructuredResult=execution.structuredContent) }
  val message=ChatMessage(id="final",text="",participant=Participant.MODEL,status=MessageStatus.SUCCESS,segments=segments)
  assertEquals(listOf(proposal.copy(sourceMessageId="final")),tracker.update(listOf(message),false))
  assertTrue(tracker.update(listOf(message),false).isEmpty())
  assertThrows(IllegalArgumentException::class.java){runBlocking{
   provider.executeOneShotResult(NotesProposalToolProvider.NAME,format.toString().replace("b".repeat(64),"c".repeat(64)),owner)
  }}
 }
 @Test fun productionAttachmentToolsSerializerAndResponseParserRunOnHost():Unit=runBlocking {
  val wireZip=System.getProperty("notes.host.zip")?.let{File(it)}
  val fixture=System.getProperty("notes.host.fixture")?.let { Json.parseToJsonElement(File(it).readText()).jsonObject }
  val supplied=wireZip?.let{NotesDocumentFormat.read(it)}
  val docId=supplied?.documentId ?: fixture?.get("documentId")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
  val revision=supplied?.revisionId ?: fixture?.get("revisionId")?.jsonPrimitive?.content ?: "a".repeat(64)
  val files=linkedMapOf("pages/first.md" to "\u200b".toByteArray(),"pages/first.json" to "{}".toByteArray())
  val image=fixture?.get("imagePath")?.jsonPrimitive?.content?.let{File(it).readBytes()}
  if(image!=null)files["pages/first.jpg"]=image
  val page=NotesPage("first","pages/first.md","pages/first.json",if(image==null)null else "pages/first.jpg",fixture?.get("imageFrame")?.jsonObject)
  val regions=supplied?.inkRegions ?: fixture?.get("inkRegions")?.let{NotesDocumentFormat.json.decodeFromJsonElement<List<NotesInkRegion>>(it)}
   ?: listOf(NotesInkRegion("ink-fixture","first","run",buildJsonObject{put("x",10);put("y",10);put("width",50);put("height",30)},listOf("stroke")))
  val doc=NotesDocument("kalamos.notes-document/v1",docId,revision,"Host note",emptyList(),listOf(page),files.mapValues{java.security.MessageDigest.getInstance("SHA-256").digest(it.value).joinToString(""){ byte -> "%02x".format(byte) }},inkRegions=regions)
  val store=NotesExchangeStore(app);val request=NotesExchangeRequest(UUID.randomUUID().toString(),"host","pick",docId,revision,ready=true)
  if(wireZip!=null)wireZip.copyTo(store.file(request.requestId),overwrite=true) else ZipOutputStream(store.file(request.requestId).outputStream()).use { zip ->
   (files+("manifest.json" to NotesDocumentFormat.json.encodeToString(doc).toByteArray())).forEach{(path,bytes)->zip.putNextEntry(ZipEntry(path));zip.write(bytes);zip.closeEntry()}
  }
  val notes=NotesProposalToolProvider(app)
  val selectionPath=System.getProperty("notes.host.selections")
  val selectionExperiment=selectionPath?.let{NotesSelectionExperiment(notes,Json.parseToJsonElement(File(it).readText()).jsonObject)}
  val originalAttachment=store.attachment(request)
  val attachment=if(selectionExperiment==null)originalAttachment else originalAttachment.copy(preparedText=selectionExperiment.context(requireNotNull(supplied)))
  val recognized=System.getProperty("notes.host.recognized")?.let { path ->
   val record=Json.parseToJsonElement(File(path).readText()).jsonObject
   (record.getValue("results").jsonArray.takeIf{it.isNotEmpty()} ?: record.getValue("stagedRecognitions").jsonArray).single().jsonObject
  }
  if(recognized!=null){require(recognized.getValue("documentId").jsonPrimitive.content==docId && recognized.getValue("baseRevisionId").jsonPrimitive.content==revision)}
  val recognition=recognized?.let { NotesDocumentFormat.json.decodeFromJsonElement<NotesProposal>(it) }
  recognition?.let { NotesRecognitionStore(app).save(it,ctx.conversationId) }
  val prepared=if(recognition==null)attachment else attachment.copy(preparedText=buildJsonObject{
   put("recognitionProposalId",recognition.proposalId)
   put("title",recognition.title)
   put("recognizedBlocks",Json.encodeToJsonElement(recognition.blocks.map { it.copy(sourceRefs=null,sourceInkRegionIds=null) }))
  }.toString())
  val payload=MessagePayloadBuilder().buildComposerPayload(listOf(prepared))
  assertFalse(attachment.preparedText!!.contains("strokeIds"));assertFalse(attachment.preparedText.contains("runId"))
  assertEquals(if(image==null && supplied?.pages?.none{it.previewFile!=null}!=false)0 else 1,payload.allImages.size)
  val entity=MessageEntity(id="user-host",conversationId="host",text=System.getProperty("notes.host.instruction")?.let{File(it).readText()} ?: "Replace readable handwriting with editable text on the same page; leave anything unreadable unchanged.",participant=Participant.USER,status=MessageStatus.SUCCESS,timestamp=1L,runId="run-host",images=if(recognized==null)payload.allImages else emptyList(),attachmentMeta=Json.encodeToString(payload.attachmentMeta))
  val message=projectProviderMessages(listOf(entity),includeStoredTranscriptions=false).single()
  if(recognition==null){assertTrue(message.text.contains(docId));assertTrue(message.text.contains(revision))}
  regions.firstOrNull()?.let { region ->
   if(selectionExperiment==null && recognition==null)assertTrue(message.text.contains(region.id)) else assertFalse(message.text.contains(region.id))
  }
  if(regions.isEmpty())supplied?.blocks?.forEach { assertTrue(message.text.contains(it.id)) }
  val skills=SkillToolProvider(SkillManager(app));val skillPath=System.getProperty("notes.host.skill")
  val skillDirectory=System.getProperty("notes.host.skillDirectory")?.let{File(it)}
  val skillFiles=skillDirectory?.listFiles()?.filter{it.isFile && it.extension=="md"}?.sortedBy{it.name}
   ?: skillPath?.let{listOf(File(it))} ?: emptyList()
  val readContext=ctx.copy(skillModifyAccess=false)
  for(file in skillFiles){
   val content=file.readText()
   val created=skills.executeOneShotResult("create_skill_file",buildJsonObject{
    put("name",if(skillDirectory==null)"kalamos-handwriting.md" else file.name)
    put("description",content.lineSequence().first().removePrefix("# "))
    put("content",content)
   }.toString(),ctx)
   assertFalse(created.isError)
  }
  val tools=(selectionExperiment?.definitions(ctx) ?: notes.definitions(ctx))+(if(skillFiles.isEmpty())emptyList() else skills.definitions(readContext))
  assertEquals(listOf("propose_notes_document","replace_notes_handwriting"),tools.take(2).map{it.function.name})
  val arguments=buildJsonObject {
   put("documentId",docId);put("baseRevisionId",revision);put("title",supplied?.title?:"Host note")
   put("targetPageIds",Json.encodeToJsonElement(supplied?.pages?.map{it.id}?:listOf("first")))
   if(regions.isEmpty())put("blocks",Json.encodeToJsonElement(requireNotNull(supplied).blocks))
   else putJsonArray("blocks"){add(buildJsonObject{put("type","paragraph");put("text","Milk");putJsonArray(if(selectionExperiment==null)"sourceInkRegionIds" else "sourceSelectionIds"){add(if(selectionExperiment==null)regions.first().id else Json.parseToJsonElement(File(selectionPath).readText()).jsonObject.getValue("selections").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.content)}})}
  }.toString()
  val sse=System.getProperty("notes.host.replay")?.let{File(it).readText()} ?: "data: "+buildJsonObject {
   putJsonArray("choices"){add(buildJsonObject{put("index",0);put("finish_reason","tool_calls");putJsonObject("delta"){putJsonArray("tool_calls"){add(buildJsonObject{put("index",0);put("id","call-host");put("type","function");putJsonObject("function"){put("name",if(regions.isEmpty())NotesProposalToolProvider.NAME else NotesProposalToolProvider.TRANSCRIBE_NAME);put("arguments",arguments)}})}}})}
  }.toString()+"\n\ndata: [DONE]\n\n"
  var captured=""
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.createContext("/"){exchange->captured=exchange.requestBody.bufferedReader().use{it.readText()};val bytes=sse.toByteArray();exchange.responseHeaders.add("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}}
  server.start()
  try {
   val history=mutableListOf(entity)
   System.getProperty("notes.host.prior")?.let { priorPath ->
    val prior=Json.parseToJsonElement(File(priorPath).readText()).jsonObject
    prior["stagedRecognitions"]?.jsonArray?.forEach { record ->
     NotesRecognitionStore(app).save(NotesDocumentFormat.json.decodeFromJsonElement<NotesProposal>(record),ctx.conversationId)
    }
    val thought=prior["exposedThinking"]!!.jsonPrimitive.content
    val segments=prior["executions"]!!.jsonArray.map{item->item.jsonObject.let{execution->MessageSegment(type="tool",toolName=execution["name"]!!.jsonPrimitive.content,toolArgs=execution["arguments"]!!.jsonPrimitive.content,toolCallId=execution["id"]!!.jsonPrimitive.content,toolResult=execution["result"]!!.jsonPrimitive.content)}}
    history+=entity.copy(id="tool_host",participant=Participant.MODEL,text="",images=emptyList(),attachmentMeta=null,toolCallJson=Json.encodeToString(listOf(MessageSegment(type="thought",content=thought))+segments),timestamp=2L)
    history+=entity.copy(id="result_host",participant=Participant.MODEL,text="",images=emptyList(),attachmentMeta=null,toolCallJson=Json.encodeToString(segments),timestamp=3L)
   }
   val messages=projectProviderMessages(history,includeStoredTranscriptions=false)
   val config=ProviderConfig(apiKey="",modelId="deepseek-flash",baseUrl="http://127.0.0.1:"+server.address.port,tools=tools,thinkingEnabled=System.getProperty("notes.host.thinking","true").toBoolean(),includeAssistantReasoning=System.getProperty("notes.host.thinking","true").toBoolean(),thinkingLevel=System.getProperty("notes.host.thinkingLevel","medium"),maxTokens=requireNotNull(System.getProperty("notes.host.maxTokens","4096")).toInt(),systemPrompt=if(skillFiles.isEmpty())null else "Read the relevant saved skill before processing this note. Available skills:\n"+SkillManager(app).catalog())
   val events=withTimeout(20_000){DeepSeekProvider().generateResponse(messages,config).toList()}
   val errors=events.filterIsInstance<StreamEvent.Error>()
   val expectedError=System.getProperty("notes.host.expectError")
   if(expectedError==null)assertFalse(errors.joinToString{it.message},errors.isNotEmpty())
   else {assertEquals(1,errors.size);assertTrue(errors.single().message.contains(expectedError))}
   assertTrue(captured.contains("replace_notes_handwriting"));assertFalse(captured.contains("strokeIds"))
   if(System.getProperty("notes.host.prior")!=null && config.thinkingEnabled){
    val prior=Json.parseToJsonElement(File(requireNotNull(System.getProperty("notes.host.prior"))).readText()).jsonObject
    if(prior.getValue("exposedThinking").jsonPrimitive.content.isNotEmpty()){
     val assistant=Json.parseToJsonElement(captured).jsonObject.getValue("messages").jsonArray.map{it.jsonObject}.single{it["role"]?.jsonPrimitive?.content=="assistant"}
     assertEquals(prior.getValue("exposedThinking").jsonPrimitive.content,assistant.getValue("reasoning_content").jsonPrimitive.content)
    }
   }
   val calls=events.filterIsInstance<StreamEvent.ToolCallsRequest>().flatMap{it.calls}+events.filterIsInstance<StreamEvent.ToolCallRequest>()
   if(expectedError!=null)assertTrue(calls.isEmpty())
   if(System.getProperty("notes.host.replay")==null)assertEquals(1,calls.size)
   val results=calls.map{call->if(call.name in setOf("read_skill_file","list_skill_files"))skills.executeOneShotResult(call.name,call.arguments,readContext) else selectionExperiment?.execute(call.name,call.arguments,ctx) ?: notes.executeOneShotResult(call.name,call.arguments,ctx)}
   results.forEach { assertFalse(it.isError);it.structuredContent?.let{content->NotesDocumentFormat.decodeProposal(content)} }
   System.getProperty("notes.host.output")?.let{path->File(path).apply{mkdirs()}.let{dir->
    File(dir,"production-request.json").writeText(captured)
    File(dir,"production-tool-results.json").writeText(buildJsonObject {
     putJsonArray("results"){results.mapNotNull{it.structuredContent}.forEach{add(Json.parseToJsonElement(it))}}
     putJsonArray("stagedRecognitions") {
      calls.zip(results).filter { (call,result) -> call.name==NotesProposalToolProvider.TRANSCRIBE_NAME && result.structuredContent==null }.forEach { (_,result) ->
       val stage=Json.parseToJsonElement(result.text.substringAfter('\n')).jsonObject
       add(Json.parseToJsonElement(NotesRecognitionStore(app).load(stage.getValue("recognitionProposalId").jsonPrimitive.content,ctx.conversationId).encode()))
      }
     }
     putJsonArray("executions"){calls.zip(results).forEach{(call,result)->add(buildJsonObject{put("name",call.name);put("id",call.id);put("arguments",call.arguments);put("result",result.text)})}}
     put("exposedThinking",events.filterIsInstance<StreamEvent.ThoughtChunk>().joinToString(""){it.thought})
     putJsonArray("errors"){errors.forEach{add(it.message)}}
     put("answer",events.filterIsInstance<StreamEvent.TextChunk>().joinToString(""){it.text})
    }.toString())
   }}
  } finally {server.stop(0)}
 }
}
