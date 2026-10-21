package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.*
import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.model.ToolImageAttachment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.UUID

/** Hermes owns sessions; this optional backend translates only the selected presentation. */
internal class HermesRemoteBackend(private val connection: RemoteConnection) : RemoteBackend {
    private val auth = HermesDashboardAuth(connection.address, connection.username, connection.token)
    private val uploads = HermesAttachmentUploads(auth)
    private val rpc = HermesRpcTransport(auth, connection.profile)
    override val address: String = connection.id
    override val capabilities = RemoteCapabilities("Hermes", attachments = true, archive = true, usage = false, activeSend = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val attach = Mutex()
    private val changes = MutableStateFlow(0L)
    private var disconnected = false
    private var binding: Binding? = null
    private val imageRows = linkedMapOf<String, Pair<String, List<String>>>()
    private val admissions = linkedMapOf<String, String>()
    private val deliveries = linkedMapOf<String, String>()
    private val receipts = linkedMapOf<String, RemoteInputReceipt>()
    private var modelCatalog: List<RemoteModel> = emptyList()
    private data class Binding(val stored: String, val runtime: String, var status: RemoteRuntime,
        var messages: List<RemoteMessage> = emptyList(), var cursor: String? = null,
        val questions: MutableMap<String, RemoteQuestion> = linkedMapOf())

    init {
        rpc.onFrame = ::frame
        rpc.onDisconnect = { synchronized(lock) {
            disconnected = true; binding = null; changes.value++
        } }
    }
    override suspend fun connect(): String { rpc.connect(); synchronized(lock) { disconnected = false }; return "Hermes" }
    override suspend fun models(): List<RemoteModel> {
        val options = rpc.call("model.options", buildJsonObject { put("include_unconfigured", false) })
        val current = options.string("model")
        val provider = options.string("provider")
        return options.array("providers").flatMap { raw ->
            val p = raw.jsonObject
            p.array("models").mapNotNull { item ->
                val name = (item as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val id = p.string("slug").orEmpty() + "/" + name
                RemoteModel(id, name, name == current && p.string("slug") == provider)
            }
        }.distinctBy { it.id }.also { modelCatalog = it }
    }
    override suspend fun sessions(cursor: String?): RemoteSessionPage {
        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        connect()
        val result = auth.get("api/sessions?limit=100&offset=$offset&order=recent&min_messages=1" + profileQuery())
        if (result.obj("storage").isNotEmpty()) throw IOException("Hermes session storage is unavailable")
        val rows = result.array("sessions").map { raw ->
            val row = raw.jsonObject
            RemoteSession(requireNotNull(row.string("id")), row.string("title").orEmpty(), row.string("cwd").orEmpty(),
                ((row.string("last_active")?.toDoubleOrNull() ?: row.string("started_at")?.toDoubleOrNull() ?: 0.0) * 1000).toLong())
        }
        val total = result.string("total")?.toIntOrNull() ?: (offset + rows.size)
        return RemoteSessionPage(rows, if (offset + rows.size < total && rows.isNotEmpty()) (offset + rows.size).toString() else null)
    }
    private suspend fun attached(id: String): Binding = attach.withLock {
        synchronized(lock) { binding?.takeIf { it.stored == id } }?.let { return@withLock it }
        val response = rpc.call("session.resume", buildJsonObject {
            put("session_id", id); put("omit_messages", true); put("defer_history", true); put("inline_images", false)
        })
        bind(id, response)
    }
    private fun bind(id: String, response: JsonObject): Binding = synchronized(lock) {
        val runtime = response.string("session_id") ?: throw IOException("Hermes session binding is missing")
        val info = response.obj("info")
        val running = response["running"]?.jsonPrimitive?.booleanOrNull == true
        val b = Binding(id, runtime, RemoteRuntime(if (running) "active" else "idle",
            activeTurnId = if (running) (response.string("turn_started_at") ?: response.string("started_at"))?.let { "$runtime:$it" } else null,
            model = info.string("provider")?.let { "$it/" }.orEmpty() + info.string("model").orEmpty(), agent = "Hermes"))
        binding = b; disconnected = false
        response.array("open_requests").forEach { raw -> serverRequest(raw.jsonObject) }
        b
    }
    private fun profileQuery(): String = if (connection.profile.isBlank()) "" else
        "&profile=" + java.net.URLEncoder.encode(connection.profile, "UTF-8")
    override suspend fun conversation(id: String, cursor: String?): RemoteConversationPage {
        val b = attached(id)
        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        val path = "api/sessions/" + java.net.URLEncoder.encode(id, "UTF-8") +
            "/messages?limit=64&order=latest&inline_images=false&offset=$offset" + profileQuery()
        val revision = changes.value
        val history = auth.get(path)
        return synchronized(lock) {
            check(binding === b) { "Hermes session changed" }
            val messages = hermesHistory(history.array("messages"), deliveries)
            val next = if (history.array("messages").size == 64) (offset + 64).toString() else null
            if (cursor == null && changes.value == revision) { b.messages = messages; b.cursor = next }
            rememberImages(id, hermesPage(if (cursor == null && changes.value != revision) b.messages else messages, b.status.copy(questions = b.questions.values.toList()), next, cursor,
                receipts.values.filter { receipt -> messages.any { it.id == receipt.messageId } }))
        }
    }
    override fun events(id: String): Flow<RemoteConversationPage> = flow {
        connect()
        emit(conversation(id))
        changes.collect {
            val page = synchronized(lock) {
                if (disconnected) throw IOException("Hermes connection interrupted")
                binding?.takeIf { it.stored == id }?.let { b ->
                    rememberImages(id, hermesPage(b.messages, b.status.copy(questions = b.questions.values.toList()), b.cursor,
                        receipts = receipts.values.toList()))
                }
            }
            if (page != null) emit(page)
        }
    }.conflate()

    override suspend fun create(text: String, clientId: String, attachments: List<String>, settings: RemoteSettings?): RemoteSession {
        requireInput(text, attachments)
        val model = settings?.model ?: throw FiloInputException()
        val result = rpc.call("session.create", buildJsonObject {
            put("source", "agora"); put("close_on_disconnect", false); put("idempotency_key", clientId)
            put("provider", model.substringBefore('/')); put("model", model.substringAfter('/'))
        })
        val id = result.string("stored_session_id") ?: throw IOException("Hermes session identity is missing")
        bind(id, result)
        val turn = send(id, text, clientId, attachments)
        return RemoteSession(id, "", result.obj("info").string("cwd").orEmpty(), System.currentTimeMillis(), acceptedTurnId = turn)
    }
    override suspend fun send(id: String, text: String, clientId: String, attachments: List<String>): String {
        requireInput(text, attachments)
        val b = attached(id)
        if (synchronized(lock) { b.status.isRunning }) throw FiloInputException()
        val submittedText = uploads.attach(b.runtime, text, attachments, rpc::call)
        val response = rpc.call("prompt.submit", buildJsonObject { put("session_id", b.runtime); put("text", submittedText) })
        // An ack proves admission, not completion. Only a native durable row proves input delivery.
        val row = response.string("user_row_id")
        val admission = response.string("admission_id")
        val turn = row?.let { "row:$it" } ?: admission?.let { "admission:$it" } ?: "unconfirmed:$clientId"
        synchronized(lock) {
            if (admission != null) { admissions[admission] = clientId; while (admissions.size > 128) admissions.remove(admissions.keys.first()) }
            if (row != null) {
                val display = hermesHistoryImages(submittedText)
                val user = RemoteMessage("row:$row", turn, clientId, "user", display.first, System.currentTimeMillis(),
                    nativeId = "row:$row", imageLinks = display.second)
                if (b.messages.none { it.id == user.id }) {
                    val boundary = b.messages.indexOfFirst { it.turnId == b.status.activeTurnId }
                    b.messages = if (boundary >= 0) b.messages.take(boundary) + user + b.messages.drop(boundary) else b.messages + user
                }
                deliveries[row] = clientId
                receipts[clientId] = RemoteInputReceipt(clientId, turn, "row:$row")
                while (deliveries.size > 128) deliveries.remove(deliveries.keys.first())
                while (receipts.size > 128) receipts.remove(receipts.keys.first())
            }
            changes.value++
        }
        return turn
    }
    private fun requireInput(text: String, attachments: List<String>) {
        if ((text.isBlank() && attachments.isEmpty()) || text.toByteArray().size > 64 * 1024 || attachments.size > REMOTE_ATTACHMENT_COUNT) throw FiloInputException()
    }
    override suspend fun rename(id: String, name: String): RemoteSession {
        val b = attached(id)
        rpc.call("session.title", buildJsonObject { put("session_id", b.runtime); put("title", name) })
        return RemoteSession(id, name, "", System.currentTimeMillis())
    }
    override suspend fun setModel(id: String, model: String) {
        val b = attached(id)
        val result = rpc.call("config.set", buildJsonObject {
            put("session_id", b.runtime); put("key", "model"); put("value", model.substringAfter('/') + " --provider " + model.substringBefore('/')); put("scope", "session")
        })
        if (result["confirm_required"]?.jsonPrimitive?.booleanOrNull == true) throw FiloInputException()
        synchronized(lock) { b.status = b.status.copy(model = model); changes.value++ }
    }
    override suspend fun updateSettings(id: String, settings: RemoteSettings) {
        if (settings.effort != null || settings.updateServiceTier || settings.model == null) throw FiloInputException()
        setModel(id, settings.model)
    }
    override suspend fun stop(id: String, turnId: String) {
        val b = attached(id)
        synchronized(lock) { if (!b.status.isRunning || b.status.activeTurnId != turnId) throw FiloInputException() }
        rpc.call("session.interrupt", buildJsonObject { put("session_id", b.runtime) })
    }
    override suspend fun answer(id: String, requestId: String, result: JsonObject) {
        val b = synchronized(lock) { binding?.takeIf { it.stored == id } } ?: throw FiloInputException()
        val q = synchronized(lock) { b.questions[requestId] } ?: throw FiloInputException()
        validateHermesAnswer(q, result)
        if (!rpc.respond(requestId, result)) throw IOException("Hermes reply outcome is unknown")
        synchronized(lock) { b.questions.remove(requestId); changes.value++ }
    }
    override suspend fun archiveSession(id: String) {
        val b = attached(id)
        val result = rpc.call("session.archive", buildJsonObject { put("session_id", b.runtime); put("archived", true) })
        if (result["archived"]?.jsonPrimitive?.booleanOrNull != true) throw IOException("Hermes archive was not confirmed")
    }
    override suspend fun usage() = RemoteUsage(emptyList())
    override suspend fun upload(item: SelectedAttachment): RemoteUpload { connect(); return uploads.upload(item) }
    private fun rememberImages(id: String, page: RemoteConversationPage): RemoteConversationPage {
        page.messages.zip(page.nodes).forEach { (message, node) ->
            if (message.imageLinks.isNotEmpty()) imageRows["$id/${message.id}"] = node.revision to message.imageLinks
        }
        while (imageRows.size > 512) imageRows.remove(imageRows.keys.first())
        return page
    }
    override suspend fun image(id: String, requested: RemotePayloadRequest, persist: (java.io.InputStream, String) -> ToolImageAttachment): ToolImageAttachment {
        val link = synchronized(lock) {
            val record = imageRows["$id/${requested.id}"]?.takeIf { it.first == requested.revision } ?: throw FiloInputException()
            record.second.getOrNull(requested.imageIndex ?: 0) ?: throw FiloInputException()
        }
        return auth.image(hermesImagePath(link), persist)
    }
    override fun close() { scope.cancel(); rpc.close() }

    private fun refreshCompletedHistory(owner: Binding) {
        val revision = changes.value + 1 // frame publishes once after this callback
        scope.launch {
            try {
                val history = auth.get("api/sessions/" + java.net.URLEncoder.encode(owner.stored, "UTF-8") +
                    "/messages?limit=64&order=latest&inline_images=false" + profileQuery())
                synchronized(lock) {
                    if (binding === owner && !disconnected && changes.value == revision) {
                        owner.messages = hermesHistory(history.array("messages"), deliveries)
                        changes.value++
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Retain the confirmed live result; the next read can hydrate history. */ }
        }
    }

    private fun serverRequest(frame: JsonObject) {
        val id = frame.string("id") ?: return
        val method = frame.string("method") ?: return
        val params = frame.obj("params")
        val b = binding
        if (method == "clarify" && params.array("questions").any { it.jsonObject["multi_select"]?.jsonPrimitive?.booleanOrNull == true }) { rpc.respond(id); return }
        if (b == null || params.string("session_id") != b.runtime) { rpc.respond(id, notShown = true); return }
        if (method !in setOf("approval", "clarify")) {
            rpc.respond(id); return
        }
        if (b.questions.size >= 16) { rpc.respond(id); return }
        b.questions[id] = RemoteQuestion(id, method, params)
    }
    private fun frame(frame: JsonObject) = synchronized(lock) {
        if (frame.string("id") != null && frame.string("method") != null) {
            serverRequest(frame); changes.value++; return@synchronized
        }
        val params = frame.obj("params")
        val b = binding ?: return@synchronized
        if (params.string("session_id") != b.runtime) return@synchronized
        val type = params.string("type") ?: return@synchronized
        val payload = params.obj("payload")
        val active = b.status.activeTurnId
        val turn = active ?: "live:${UUID.randomUUID()}"
        fun updateMessage(key: String, transform: (RemoteMessage) -> RemoteMessage) {
            val message = b.messages.firstOrNull { it.id == key }
                ?: RemoteMessage(key, turn, null, "assistant", "", System.currentTimeMillis())
            val updated = transform(message)
            if (updated.text.toByteArray().size > MAX_REMOTE_RESPONSE_BYTES / 2) throw RemoteContentLimitException()
            b.messages = if (b.messages.any { it.id == key }) b.messages.map { if (it.id == key) updated else it }
                else b.messages + updated
            if (b.messages.size > 256 || b.messages.sumOf { it.text.length.toLong() * 2 +
                (it.activity?.arguments?.length ?: 0) * 2L + (it.activity?.result?.length ?: 0) * 2L } > 8L * 1024 * 1024)
                throw RemoteContentLimitException()
        }
        when (type) {
            "message.start" -> b.status = b.status.copy(status = "active", activeTurnId = turn)
            "message.delta" -> {
                b.status = b.status.copy(status = "active", activeTurnId = turn)
                updateMessage("$turn:answer") { it.copy(text = it.text + payload.string("text").orEmpty()) }
            }
            "reasoning.delta", "thinking.delta" -> updateMessage("$turn:thought") {
                it.copy(activity = RemoteActivity("thought"), text = it.text + payload.string("text").orEmpty())
            }
            "tool.start", "tool.complete" -> {
                val tool = payload.string("tool_id") ?: return@synchronized
                updateMessage("$turn:tool:$tool") { it.copy(activity = RemoteActivity("tool",
                    payload.string("name") ?: "Tool", payload["args"]?.toString(),
                    payload.string("result_text") ?: payload.string("summary") ?: payload["result"]?.toString(),
                    if (type == "tool.start") "running" else "succeeded")) }
            }
            "message.complete" -> {
                val status = payload.string("status")
                val failed = status in setOf("failed", "error", "interrupted", "cancelled") || payload.string("error") != null || payload["partial"]?.jsonPrimitive?.booleanOrNull == true
                updateMessage("$turn:answer") { it.copy(text = payload.string("text")?.takeIf(String::isNotEmpty) ?: it.text,
                    error = failed) }
                b.status = b.status.copy(status = "idle", activeTurnId = null,
                    completedTurnId = if (!failed && status == "complete" && payload.obj("persisted_turn")["complete"]?.jsonPrimitive?.booleanOrNull == true)
                        payload.obj("persisted_turn").string("final_assistant_row_id")?.let { "row:$it" } else b.status.completedTurnId)
                b.questions.clear()
                val persisted = payload.obj("persisted_turn")
                val admission = persisted.string("submission_id")
                val client = admissions[admission]
                val userRow = persisted.string("user_row_id")
                if (client != null && userRow != null && client !in receipts) {
                    deliveries[userRow] = client
                    receipts[client] = RemoteInputReceipt(client, "admission:$admission", "row:$userRow")
                    while (receipts.size > 128) receipts.remove(receipts.keys.first())
                    while (deliveries.size > 128) deliveries.remove(deliveries.keys.first())
                }
                refreshCompletedHistory(b)
            }
            "error", "turn.error" -> {
                updateMessage("$turn:answer") { it.copy(text = payload.string("message") ?: "Hermes reported an error", error = true) }
                b.status = b.status.copy(status = "idle", activeTurnId = null)
            }
            "request.cancel" -> b.questions.remove(payload.string("id") ?: payload.string("request_id"))
            "session.info" -> b.status = b.status.copy(model = payload.string("provider")?.let { "$it/" }.orEmpty() + payload.string("model").orEmpty())
        }
        changes.value++
    }
}
