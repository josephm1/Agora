package com.newoether.agora.notes

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.newoether.agora.MainActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.ui.components.applyEinkPresentation
import kotlinx.coroutines.withContext

/** Internal activity: only Agora may supply the originating draft or a proposal. */
class NotesExchangeActivity : ComponentActivity() {
    private lateinit var requestId: String
    private var pendingReturn: Intent? = null
    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { cancelExchange(); return@registerForActivityResult }
        pendingReturn = result.data
        receiveReturn()
    }
    private fun receiveReturn() {
        lifecycleScope.launch {
            try {
                val data = requireNotNull(pendingReturn) { "Notes returned no document" }
                require(data.getStringExtra("requestId") == requestId) { "Notes returned an unrelated request" }
                val uri = requireNotNull(data.data) { "Notes returned no document URI" }
                withContext(Dispatchers.IO) {
                    val store = NotesExchangeStore(this@NotesExchangeActivity)
                    val request = requireNotNull(store.load(requestId))
                    if (!request.consumed) {
                        val count=data.getIntExtra("documentCount",1)
                        if(count>1){
                            val clip=requireNotNull(data.clipData);require(clip.itemCount==count && count in 2..32)
                            store.receiveMany(request,(0 until count).map { requireNotNull(clip.getItemAt(it).uri) })
                        } else store.receive(if(data.getBooleanExtra("recovered",false)&&request.operation=="edit")request.copy(documentId=null) else request,uri)
                    }
                }
                openInbox(this@NotesExchangeActivity, requestId)
                finish()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                val eInk = SettingsManager(applicationContext).eInkMode.first()
                val dialog = android.app.AlertDialog.Builder(this@NotesExchangeActivity)
                    .setTitle("Could not attach note")
                    .setMessage((error.message ?: "Notes return failed") + "\nYour note remains saved in Kalamos Notes.")
                    .setPositiveButton("Retry") { _, _ -> receiveReturn() }
                    .setNegativeButton("Back to Agora") { _, _ -> cancelExchange() }
                    .setOnCancelListener { cancelExchange() }.show()
                if (eInk) dialog.window?.applyEinkPresentation()
            }
        }
    }
    private fun cancelExchange() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val store = NotesExchangeStore(this@NotesExchangeActivity)
                runCatching { store.load(requestId)?.takeUnless { it.ready }?.let { store.save(it.copy(cancelled = true)) } }
            }
            openAgora(this@NotesExchangeActivity)
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = savedInstanceState?.getString("requestId") ?: intent.getStringExtra("requestId") ?: UUID.randomUUID().toString()
        current = java.lang.ref.WeakReference(this)
        pendingReturn = savedInstanceState?.let { androidx.core.os.BundleCompat.getParcelable(it, "pendingReturn", Intent::class.java) }
        if (pendingReturn != null) { receiveReturn(); return }
        lifecycleScope.launch {
            try {
                val store = NotesExchangeStore(this@NotesExchangeActivity)
                val existing = withContext(Dispatchers.IO) { store.load(requestId) }
                if (existing != null) {
                    if (existing.ready && !existing.consumed) { openInbox(this@NotesExchangeActivity, requestId); finish(); return@launch }
                    if (existing.cancelled || existing.consumed) { openAgora(this@NotesExchangeActivity); finish(); return@launch }
                    if (existing.launched) {
                        launched = true
                        // Saved activity state restores the ActivityResultRegistry. Without it,
                        // resurrecting an old launch intent must never open another editor/note.
                        if (savedInstanceState == null) cancelExchange()
                        return@launch
                    }
                }
                val operation = intent.getStringExtra("operation") ?: "new"
                require(operation in setOf("new", "pick", "edit", "apply"))
                val owner = requireNotNull(intent.getStringExtra("ownerId"))
                val proposal = intent.getStringExtra("proposal")?.let(NotesDocumentFormat::decodeProposal)
                val request = NotesExchangeRequest(requestId, owner, operation,
                    proposal?.documentId ?: intent.getStringExtra("documentId"),
                    proposal?.baseRevisionId ?: intent.getStringExtra("baseRevisionId"))
                val launch = Intent("com.kalamos.notes.action.EDIT_DOCUMENT").apply {
                    component = ComponentName("com.kalamos.notebook", "com.kalamos.notebook.exchange.AgoraDocumentActivity")
                    putExtra("includeImages",intent.getBooleanExtra("includeImages",true))
                    putExtra("requestId", requestId); putExtra("ownerId", owner); putExtra("operation", operation)
                    request.documentId?.let { putExtra("documentId", it) }
                    request.baseRevisionId?.let { putExtra("baseRevisionId", it) }
                }
                withContext(Dispatchers.IO) {
                    store.save(request)
                    intent.getStringExtra("bundlePath")?.takeIf { proposal == null }?.let { path ->
                        val source = File(path)
                        val document = NotesDocumentFormat.read(source)
                        require(document.documentId == request.documentId && document.revisionId == request.baseRevisionId) { "Attachment identity mismatch" }
                        val file = File(cacheDir, "shared/notes-$requestId.zip").apply { parentFile?.mkdirs() }
                        source.copyTo(file, overwrite = true)
                        val uri = FileProvider.getUriForFile(this@NotesExchangeActivity, "$packageName.fileprovider", file)
                        launch.setDataAndType(uri, NotesDocumentFormat.MIME)
                        launch.clipData = ClipData.newRawUri("Notes document", uri)
                        launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    proposal?.let {
                        val file = File(cacheDir, "shared/notes-$requestId.json").apply { parentFile?.mkdirs(); writeText(it.encode()) }
                        val uri = FileProvider.getUriForFile(this@NotesExchangeActivity, "$packageName.fileprovider", file)
                        launch.setDataAndType(uri, NotesDocumentFormat.PROPOSAL_MIME)
                        launch.clipData = ClipData.newRawUri("Notes proposal", uri)
                        launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.getStringExtra("notesResultTrace")?.takeIf { raw -> raw.toByteArray().size<=128_000 }?.let { raw ->
                            val trace=File(cacheDir,"shared/notes-trace-$requestId.json").apply { writeText(raw) }
                            val traceUri=FileProvider.getUriForFile(this@NotesExchangeActivity,"$packageName.fileprovider",trace)
                            launch.clipData?.addItem(ClipData.Item(traceUri))
                            launch.putExtra("notesResultTraceUri",traceUri.toString())
                        }
                    }
                }
                withContext(Dispatchers.IO) { store.save(request.copy(launched = true)) }
                launched = true
                editor.launch(launch)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                Toast.makeText(this@NotesExchangeActivity, error.message ?: "Install Kalamos Notes to continue", Toast.LENGTH_LONG).show(); cancelExchange()
            }
        }
    }
    override fun onDestroy() {
        if (current.get() === this) current.clear()
        super.onDestroy()
    }
    companion object {
        private var current = java.lang.ref.WeakReference<NotesExchangeActivity>(null)
        fun isLive(id: String): Boolean = current.get()?.let { it.requestId == id && !it.isFinishing && !it.isDestroyed } == true
        fun resumeIntent(id: String): Intent? = current.get()?.takeIf { isLive(id) }?.let {
            Intent(it.intent).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }
    }
    private var launched = false
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("requestId", requestId); outState.putBoolean("launched", launched); outState.putParcelable("pendingReturn", pendingReturn); super.onSaveInstanceState(outState) }
}

/** External ingress never trusts a supplied draft owner. The user chooses its destination in Agora. */
class NotesInboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = savedInstanceState?.getString("requestId") ?: UUID.randomUUID().toString()
        requestId = id
        lifecycleScope.launch {
            try {
                require(intent.action == ACTION_ATTACH && intent.type == NotesDocumentFormat.MIME) { "Unsupported Notes handoff" }
                val store = NotesExchangeStore(this@NotesInboxActivity)
                withContext(Dispatchers.IO) {
                    if (store.load(id)?.ready != true) {
                        val request = NotesExchangeRequest(id, null, "attach", linkedOwnerId = intent.getStringExtra("ownerId"))
                        store.save(request); store.receive(request, requireNotNull(intent.data))
                    }
                }
                openInbox(this@NotesInboxActivity, id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Toast.makeText(this@NotesInboxActivity, error.message ?: "Notes import failed", Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    private var requestId: String? = null
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("requestId", requestId); super.onSaveInstanceState(outState) }
}
const val ACTION_ATTACH = "com.kalamos.notes.action.ATTACH_DOCUMENT"
const val EXTRA_NOTES_REQUEST = "com.newoether.agora.extra.NOTES_REQUEST"
private fun openAgora(context: Context, requestId: String? = null) {
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        requestId?.let { putExtra(EXTRA_NOTES_REQUEST, it) }
    })
}

private fun openInbox(context: Context, requestId: String) = openAgora(context, requestId)
