package com.newoether.agora.notes

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.newoether.agora.model.*
import com.newoether.agora.viewmodel.MessagePayloadBuilder
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesBackgroundTest {
    private fun request()=NotesBackgroundRequest(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"a".repeat(64),"Tasks")
    private fun proposal(request:NotesBackgroundRequest)=NotesProposal(proposalId=UUID.randomUUID().toString(),
        documentId=request.documentId,baseRevisionId=request.revisionId,title=request.title,
        blocks=listOf(NotesBlock("one","task","Milk",false)),backgroundRequestId=request.requestId)
    @Test fun userTriggeredProcessingQueuesAnExpeditedNetworkJob() {
        val id=UUID.randomUUID().toString()
        val work=notesProcessWorkRequest(id)
        assertTrue(work.workSpec.expedited)
        assertEquals(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,work.workSpec.outOfQuotaPolicy)
        assertEquals(androidx.work.NetworkType.CONNECTED,work.workSpec.constraints.requiredNetworkType)
        assertEquals(id,work.workSpec.input.getString("requestId"))
        assertThrows(IllegalArgumentException::class.java) { notesProcessWorkRequest("bad") }
    }
    @Test fun durableReceiptAndConversationIdentitySurviveRecreation() {
        val app=RuntimeEnvironment.getApplication();val request=request();val store=NotesBackgroundStore(app)
        store.save(request.copy(state="running"))
        assertEquals(request.conversationId,NotesBackgroundStore(app).load(request.requestId)!!.conversationId)
        assertEquals("running",NotesBackgroundStore(app).load(request.requestId)!!.state)
        store.saveProposal(request.requestId,proposal(request));store.save(request.copy(state="done"))
        assertEquals("done",NotesBackgroundStore(app).load(request.requestId)!!.state)
        assertEquals(request.documentId,NotesDocumentFormat.decodeProposal(store.proposal(request.requestId).readText()).documentId)
        assertThrows(IllegalArgumentException::class.java) { store.load("../outside") }
    }
    @Test fun recoveryRequiresExactRequestRevisionAndSuccessfulStructuredNotesResult() {
        val request=request();val proposal=proposal(request)
        val segment=MessageSegment(type="tool",toolName="replace_notes_handwriting",toolState=ToolExecutionStates.SUCCEEDED,
            toolStructuredResult=proposal.encode())
        assertEquals(proposal,notesBackgroundProposal(request,listOf(segment)))
        assertNull(notesBackgroundProposal(request.copy(revisionId="b".repeat(64)),listOf(segment)))
        assertNull(notesBackgroundProposal(request.copy(requestId=UUID.randomUUID().toString()),listOf(segment)))
        assertNull(notesBackgroundProposal(request,listOf(segment.copy(toolState=ToolExecutionStates.FAILED))))
        assertNull(notesBackgroundProposal(request,listOf(segment.copy(toolStructuredResult=null,toolResult="# Tasks"))))
        val message=ChatMessage(text="Done",participant=Participant.MODEL,segments=listOf(segment))
        val tracker=NotesAutomaticReturn();tracker.update(emptyList(),false);tracker.update(emptyList(),true)
        assertTrue(tracker.update(listOf(message),false).isEmpty())
    }
    @Test fun headlessAttachmentUsesExistingImagePayloadAndApprovedPromptWithoutGlobalSettings() {
        val attachment=SelectedAttachment(uri="file:///note.zip",type="file",localPath="/note.zip",mimeType=NotesDocumentFormat.MIME,
            preparedText="legacy",notesMarkdownText=NotesMarkdownPrompt.TEXT,preRenderedPaths=listOf("/page.jpg"),notesThumbnailPath="/cover.jpg")
        val payload=MessagePayloadBuilder().buildComposerPayload(listOf(attachment))
        assertEquals(listOf("/page.jpg"),payload.allImages)
        val meta=payload.attachmentMeta!!.items.single()
        assertEquals(NotesMarkdownPrompt.TEXT,meta.textContent)
        assertEquals("/cover.jpg",meta.notesThumbnailPath)
        assertEquals(meta,Json.decodeFromString<AttachmentMeta>(Json.encodeToString(AttachmentMeta(listOf(meta)))).items.single())
    }
    @Test fun untrustedSourceDoesNotQueueOrLaunchActivity() {
        val app=RuntimeEnvironment.getApplication()
        NotesProcessReceiver().onReceive(app,Intent(ACTION_PROCESS_DOCUMENT).setDataAndType(Uri.parse("content://attacker/note"),NotesDocumentFormat.MIME)
            .putExtra("requestId",UUID.randomUUID().toString()))
        assertNull(org.robolectric.Shadows.shadowOf(app).nextStartedActivity)
    }
}
