package com.newoether.agora.notes

import android.app.Application
import android.content.pm.*
import android.graphics.Bitmap
import androidx.room.Room
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.api.openai.DeepSeekProvider
import com.newoether.agora.data.local.*
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.model.*
import com.newoether.agora.tool.NotesProposalToolProvider
import com.newoether.agora.viewmodel.*
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises the engine's production attachment seam, real Room writer and real provider codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesHeadlessPipelineTest {
    @Test fun imageAndApprovedPromptProduceRequestBoundResultInInspectableRoomChat():Unit=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        com.newoether.agora.util.DebugLog.forceEnabled=false;com.newoether.agora.util.DebugLog.init(app)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName="com.kalamos.notebook";applicationInfo=ApplicationInfo().apply { packageName="com.kalamos.notebook" }
            activities=arrayOf(ActivityInfo().apply { packageName="com.kalamos.notebook";name="com.kalamos.notebook.exchange.AgoraDocumentActivity";applicationInfo=ApplicationInfo().apply { packageName="com.kalamos.notebook" } })
        })
        val request=NotesBackgroundRequest(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"a".repeat(64),"Tasks")
        val image=ByteArrayOutputStream().also { Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG,90,it) }.toByteArray()
        val files=mapOf("pages/first.md" to byteArrayOf(),"pages/first.json" to "{}".toByteArray(),"pages/first.jpg" to image)
        val checksums=files.mapValues { java.security.MessageDigest.getInstance("SHA-256").digest(it.value).joinToString("") { byte -> "%02x".format(byte) } }
        val document=NotesDocument("kalamos.notes-document/v1",request.documentId,request.revisionId,request.title,emptyList(),
            listOf(NotesPage("first","pages/first.md","pages/first.json","pages/first.jpg")),checksums,
            inkRegions=listOf(NotesInkRegion("ink-one","first","run-one",buildJsonObject { put("x",0);put("y",0);put("width",40);put("height",20) },listOf("stroke-one"))))
        val exchange=NotesExchangeStore(app)
        ZipOutputStream(exchange.file(request.requestId).outputStream()).use { zip ->
            (files+mapOf("manifest.json" to Json.encodeToString(document).toByteArray())).forEach { (path,bytes) -> zip.putNextEntry(ZipEntry(path));zip.write(bytes);zip.closeEntry() }
        }
        val attachment=exchange.attachment(NotesExchangeRequest(request.requestId,request.conversationId,"background",ready=true))
        val input=headlessAttachmentInput(listOf(attachment))
        assertEquals(document,input.notesDocument)
        val context=GenerationContext(conversationId=request.conversationId,notesBackgroundProcessing=true,
            notesBackgroundRequestId=request.requestId,notesMarkdownDocument=input.notesDocument)
        val tools=NotesProposalToolProvider(app)
        val definitions=tools.definitions(context)
        assertEquals(setOf("markdown"),definitions.single().function.parameters.properties.keys)
        val db=Room.inMemoryDatabaseBuilder(app,ChatDatabase::class.java).allowMainThreadQueries().build()
        val repository=ConversationRepository(db.chatDao(),db,scheduleMaintenance={})
        val runId=UUID.randomUUID().toString()
        val writer=AcceptedInputGraphWriter(repository)
        val graph=writer.commit(AcceptedInputGraphWriter.Request(
            inputEffect=RunEffect.PersistAcceptedInput(RunEffectIdentity(request.conversationId,1L,runId,0,"background-send")),
            userMessageId="background-user",modelMessageId="background-model",userText="Format this note.",
            images=input.payload.allImages,attachmentMeta=Json.encodeToString(input.payload.attachmentMeta!!),
            modelId="DeepSeek:deepseek-flash",userTimestamp=1L,touchConversationOnAdmission=false,
            newConversation=ChatEntity(request.conversationId,"Format with AI · Tasks")))
        assertEquals(listOf(attachment.preRenderedPaths!!.single()),repository.getMessage(graph.userMessage.id)!!.images)
        val markdown="# Tasks\n- [ ] Milk"
        val arguments=buildJsonObject { put("markdown",markdown) }.toString()
        val sse="data: "+buildJsonObject {
            putJsonArray("choices") { add(buildJsonObject {
                put("index",0);put("finish_reason","tool_calls");putJsonObject("delta") { putJsonArray("tool_calls") { add(buildJsonObject {
                    put("index",0);put("id","call-background");put("type","function");putJsonObject("function") { put("name",NotesProposalToolProvider.TRANSCRIBE_NAME);put("arguments",arguments) }
                }) } }
            }) }
        }+"\n\ndata: [DONE]\n\n"
        var captured=""
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { call -> captured=call.requestBody.bufferedReader().use { it.readText() };val bytes=sse.toByteArray()
            call.responseHeaders.add("Content-Type","text/event-stream");call.sendResponseHeaders(200,bytes.size.toLong());call.responseBody.use { it.write(bytes) } }
        server.start()
        try {
            val events=withTimeout(20000) { DeepSeekProvider().generateResponse(projectProviderMessages(listOf(graph.userMessage),false),
                ProviderConfig(apiKey="",modelId="deepseek-flash",baseUrl="http://127.0.0.1:${server.address.port}",tools=definitions)).toList() }
            assertTrue(captured.contains("image_url"));assertTrue(captured.contains(NotesMarkdownPrompt.TEXT))
            assertFalse(captured.contains(request.documentId));assertFalse(captured.contains("stroke-one"))
            val calls=events.filterIsInstance<StreamEvent.ToolCallsRequest>().flatMap { it.calls }+events.filterIsInstance<StreamEvent.ToolCallRequest>()
            assertEquals(1,calls.size)
            val result=tools.executeOneShotResult(calls.single().name,calls.single().arguments,context)
            val segment=MessageSegment(type="tool",toolName=calls.single().name,toolState=ToolExecutionStates.SUCCEEDED,toolStructuredResult=result.structuredContent)
            repository.upsertMessage(graph.modelMessage.copy(text="Processed",status=MessageStatus.SUCCESS,toolCallJson=Json.encodeToString(listOf(segment))))
            val durable=repository.getMessage(graph.modelMessage.id)!!
            val proposal=notesBackgroundProposal(request,Json.decodeFromString(durable.toolCallJson!!))!!
            assertEquals(markdown,proposal.markdown);assertEquals(request.documentId,proposal.documentId)
            val store=NotesBackgroundStore(app);store.saveProposal(request.requestId,proposal);store.save(request.copy(state="done"))
            assertEquals("done",store.load(request.requestId)!!.state)
            assertNotNull(repository.getConversation(request.conversationId))
        } finally { server.stop(0);db.close() }
    }
}
