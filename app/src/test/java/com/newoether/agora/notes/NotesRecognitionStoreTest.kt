package com.newoether.agora.notes

import android.app.Application
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesRecognitionStoreTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun proposal()=NotesProposal(proposalId=UUID.randomUUID().toString(),documentId=UUID.randomUUID().toString(),
        baseRevisionId="a".repeat(64),title="To do",operation="replace_ink",targetPageIds=listOf("first"),
        blocks=listOf(NotesBlock("line","paragraph","Buy milk",sourceInkRegionIds=listOf("ink-milk"))))
    @Test fun storeSurvivesRecreationAndRejectsAnotherConversationOrMissingOwner(){
        val p=proposal();NotesRecognitionStore(app).save(p,"chat")
        assertEquals(p,NotesRecognitionStore(app).load(p.proposalId,"chat"))
        assertThrows(IllegalArgumentException::class.java){NotesRecognitionStore(app).load(p.proposalId,"other")}
        assertThrows(IllegalArgumentException::class.java){NotesRecognitionStore(app).save(p,null)}
        assertThrows(IllegalArgumentException::class.java){NotesRecognitionStore(app).load("../outside","chat")}
    }
    @Test fun expiredFutureAndCorruptIdentityRecordsDoNotBecomeEdits(){
        val store=NotesRecognitionStore(app);val p=proposal();store.save(p,"chat")
        val file=File(app.filesDir,"notes-recognition/${p.proposalId}.json")
        val saved=Json.parseToJsonElement(file.readText()).jsonObject
        for(time in listOf(0L,System.currentTimeMillis()+86400000)){
            file.writeText(JsonObject(saved+("createdAt" to JsonPrimitive(time))).toString())
            assertThrows(IllegalArgumentException::class.java){store.load(p.proposalId,"chat")}
        }
        val changed=saved.getValue("proposal").jsonObject+("proposalId" to JsonPrimitive(UUID.randomUUID().toString()))
        file.writeText(JsonObject(saved+("proposal" to JsonObject(changed))).toString())
        assertThrows(IllegalArgumentException::class.java){store.load(p.proposalId,"chat")}
    }
    @Test fun retentionIsBoundedAndCleanupDoesNotDeleteOtherAppFiles(){
        val directory=File(app.filesDir,"notes-recognition").apply{mkdirs()}
        val unrelated=File(directory,"keep.txt").apply{writeText("untouched")}
        val store=NotesRecognitionStore(app)
        repeat(30){store.save(proposal(),"chat")}
        assertTrue(directory.listFiles()!!.count{it.extension=="json"}<=24)
        assertEquals("untouched",unrelated.readText())
    }
}
