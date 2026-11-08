package com.example.todoflow.data.remote

/** Minimal STOMP 1.2 frame handling for the backend's `/ws` endpoint (text frames over WebSocket). */
data class StompFrame(
    val command: String,
    val headers: Map<String, String>,
    val body: String
) {
    companion object {
        private const val NUL = "\u0000"

        fun connect(token: String): String =
            "CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer $token\n\n$NUL"

        fun subscribe(id: String, destination: String): String =
            "SUBSCRIBE\nid:$id\ndestination:$destination\n\n$NUL"

        /** Parses a text frame; returns null for heart-beats (blank lines) and malformed input. */
        fun parse(text: String): StompFrame? {
            val trimmed = text.trimStart('\n', '\r')
            if (trimmed.isBlank()) return null
            val separator = trimmed.indexOf("\n\n")
            val head = if (separator >= 0) trimmed.substring(0, separator) else trimmed.removeSuffix(NUL)
            val body = if (separator >= 0) trimmed.substring(separator + 2).removeSuffix(NUL) else ""
            val lines = head.split("\n").map { it.removeSuffix("\r") }
            val command = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon > 0) line.substring(0, colon) to line.substring(colon + 1) else null
            }.toMap()
            return StompFrame(command, headers, body)
        }
    }
}
