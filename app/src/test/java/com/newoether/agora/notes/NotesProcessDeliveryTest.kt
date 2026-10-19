package com.newoether.agora.notes

import android.app.Application
import android.content.*
import android.net.Uri
import androidx.core.content.FileProvider
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesProcessDeliveryTest {
    @Before fun resetFileProviderRootCache() {
        // Android has one stable app cache directory. Robolectric creates a new directory
        // per test while AndroidX's static URI strategy otherwise retains the previous one.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible=true }
            .get(null).let { (it as MutableMap<*,*>).clear() }
    }
    private class RecordingContext(base:Context):ContextWrapper(base) {
        val events=mutableListOf<String>()
        var grantedUri:Uri?=null
        var delivered:Intent?=null
        override fun grantUriPermission(packageName:String,uri:Uri,flags:Int) {
            assertEquals("com.kalamos.notebook",packageName)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,flags)
            events+="grant";grantedUri=uri
        }
        override fun sendBroadcast(intent:Intent) { events+="broadcast";delivered=intent }
    }
    private fun completed(context:Context):NotesBackgroundRequest {
        val request=NotesBackgroundRequest(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"a".repeat(64),"Tasks",state="done")
        val store=NotesBackgroundStore(context)
        store.save(request)
        store.saveProposal(request.requestId,NotesProposal(proposalId=UUID.randomUUID().toString(),documentId=request.documentId,
            baseRevisionId=request.revisionId,title=request.title,blocks=listOf(NotesBlock("one","paragraph","Milk")),backgroundRequestId=request.requestId))
        return request
    }
    @Test fun grantForActualFileProviderUriPrecedesExplicitNotesBroadcast() {
        val app=RuntimeEnvironment.getApplication();val context=RecordingContext(app);val request=completed(app)
        val file=NotesBackgroundStore(app).proposal(request.requestId)
        val actual=FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",file)
        sendNotesProcessResult(context,request.requestId,file,conversationId=request.conversationId)
        assertEquals(listOf("grant","broadcast"),context.events)
        assertEquals(actual,context.grantedUri)
        val intent=context.delivered!!
        assertEquals(actual,intent.data);assertEquals(actual,intent.clipData!!.getItemAt(0).uri)
        assertEquals(NotesDocumentFormat.PROPOSAL_MIME,intent.type)
        assertEquals(ComponentName("com.kalamos.notebook","com.kalamos.notebook.exchange.NotesProcessResultReceiver"),intent.component)
        assertEquals(request.requestId,intent.getStringExtra("requestId"))
    }
    @Test fun completedReplayResendsSameSavedProposalWithoutWorkOrModelAndRejectsWrongScope() {
        val app=RuntimeEnvironment.getApplication();val context=RecordingContext(app);val request=completed(app)
        val provider=NotesExchangeStoreTest.Provider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply { authority="com.kalamos.notebook.agora-documents" })
        val source=java.io.File.createTempFile("retained-note-",".zip",app.cacheDir).apply { writeText("retained source") }
        provider.files["/retained"]=source
        ShadowContentResolver.registerProviderInternal("com.kalamos.notebook.agora-documents",provider)
        val uri=Uri.parse("content://com.kalamos.notebook.agora-documents/retained")
        val before=NotesBackgroundStore(app).proposal(request.requestId).readText()
        replayNotesProcessResult(context,request.requestId,uri,request.documentId,request.revisionId)
        assertEquals(listOf("grant","broadcast"),context.events)
        assertEquals(before,NotesBackgroundStore(app).proposal(request.requestId).readText())
        assertEquals("done",NotesBackgroundStore(app).load(request.requestId)!!.state)
        val diagnosticContext=RecordingContext(app)
        replayNotesProcessResult(diagnosticContext,request.requestId,uri,request.documentId,request.revisionId,diagnosticOnly=true)
        assertEquals(listOf("grant"),diagnosticContext.events)
        assertNull(diagnosticContext.delivered)
        assertEquals(context.grantedUri,diagnosticContext.grantedUri)
        assertEquals(before,NotesBackgroundStore(app).proposal(request.requestId).readText())
        assertEquals("done",NotesBackgroundStore(app).load(request.requestId)!!.state)
        assertThrows(IllegalArgumentException::class.java) { replayNotesProcessResult(context,request.requestId,uri,request.documentId,"b".repeat(64)) }
        val pendingContext=RecordingContext(app)
        NotesBackgroundStore(app).save(request.copy(state="running"))
        replayNotesProcessResult(pendingContext,request.requestId,uri,request.documentId,request.revisionId)
        assertTrue(pendingContext.events.isEmpty())
        NotesBackgroundStore(app).save(request)
        NotesBackgroundStore(app).proposal(request.requestId).delete()
        assertThrows(IllegalStateException::class.java) { replayNotesProcessResult(context,request.requestId,uri,request.documentId,request.revisionId) }
    }
}
