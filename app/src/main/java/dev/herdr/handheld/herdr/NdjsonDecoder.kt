package dev.herdr.handheld.herdr

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlinx.serialization.json.*

class ContractException(message: String) : Exception(message)

class NdjsonDecoder(private val maxLineBytes: Int = 2 * 1024 * 1024, private val onLine: (String) -> Unit) {
    private val pending = ByteArrayOutputStream()
    fun feed(bytes: ByteArray, count: Int = bytes.size) {
        require(count in 0..bytes.size)
        for (i in 0 until count) {
            val byte = bytes[i]
            if (byte == 10.toByte()) {
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                val line = try { decoder.decode(ByteBuffer.wrap(pending.toByteArray())).toString().trimEnd('\r') }
                    catch (_: Exception) { throw ContractException("Invalid UTF-8 in terminal envelope") }
                pending.reset()
                if (line.isNotBlank()) onLine(line)
            } else {
                if (pending.size() >= maxLineBytes) { pending.reset(); throw ContractException("Terminal envelope exceeds limit") }
                pending.write(byte.toInt())
            }
        }
    }
    fun end() { if (pending.size() != 0) { pending.reset(); throw ContractException("Incomplete terminal envelope") } }
}

object HerdrWire {
    private val json = Json { ignoreUnknownKeys = true }
    fun envelope(line: String): TerminalEvent {
        val obj = try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { throw ContractException("Malformed terminal JSON") }
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "terminal.frame" -> {
                if (obj["encoding"]?.jsonPrimitive?.content != "ansi") throw ContractException("Unsupported terminal encoding")
                val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
                val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
                if (width !in 1..500 || height !in 1..300) throw ContractException("Invalid terminal geometry")
                val bytes = try { Base64.getDecoder().decode(obj.getValue("bytes").jsonPrimitive.content) }
                    catch (_: Exception) { throw ContractException("Invalid terminal bytes") }
                if (bytes.size > 1024 * 1024) throw ContractException("Terminal frame exceeds limit")
                TerminalEvent.Frame(TerminalFrame(obj["seq"]?.jsonPrimitive?.longOrNull ?: throw ContractException("Missing frame sequence"),
                    width, height, obj["full"]?.jsonPrimitive?.booleanOrNull ?: throw ContractException("Missing full-frame flag"), bytes))
            }
            "terminal.closed" -> TerminalEvent.Closed("Remote terminal stream closed")
            else -> throw ContractException("Unsupported terminal record")
        }
    }

    fun agents(raw: String, profile: HostProfile): List<AgentTarget> {
        val result = json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject ?: throw ContractException("Missing agent result")
        val agents = result["agents"]?.jsonArray ?: throw ContractException("Missing agents")
        return agents.map { element ->
            val a = element.jsonObject
            fun string(key: String) = a[key]?.jsonPrimitive?.contentOrNull
            val ref = TargetRef(profile.id, profile.session, string("terminal_id") ?: throw ContractException("Missing terminal ID"),
                string("pane_id") ?: throw ContractException("Missing pane ID"), string("agent"),
                (a["agent_session"] as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull)
            AgentTarget(ref, string("name") ?: string("terminal_title_stripped") ?: string("terminal_title") ?: ref.paneId,
                string("agent_status") ?: "unknown", string("workspace_id").orEmpty())
        }
    }

    fun input(bytes: ByteArray): String {
        require(bytes.size <= 32 * 1024)
        return buildJsonObject { put("type", "terminal.input"); put("bytes", Base64.getEncoder().encodeToString(bytes)) }.toString() + "\n"
    }
    fun resize(cols: Int, rows: Int): String {
        require(cols in 2..500 && rows in 2..300)
        return "{\"type\":\"terminal.resize\",\"cols\":$cols,\"rows\":$rows}\n"
    }
    const val release = "{\"type\":\"terminal.release\"}\n"
}
