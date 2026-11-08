package com.example.todoflow.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StompFrameTest {

    @Test
    fun connectFrameCarriesTheBearerToken() {
        val frame = StompFrame.connect("abc.def.ghi")
        assertTrue(frame.startsWith("CONNECT\n"))
        assertTrue(frame.contains("Authorization:Bearer abc.def.ghi\n"))
        assertTrue(frame.endsWith("\n\n\u0000"))
    }

    @Test
    fun subscribeFrameNamesTheDestination() {
        val frame = StompFrame.subscribe("tasks", "/topic/users/42/tasks")
        assertEquals("SUBSCRIBE\nid:tasks\ndestination:/topic/users/42/tasks\n\n\u0000", frame)
    }

    @Test
    fun parsesConnected() {
        val frame = StompFrame.parse("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000")
        assertNotNull(frame)
        assertEquals("CONNECTED", frame!!.command)
        assertEquals("1.2", frame.headers["version"])
        assertEquals("", frame.body)
    }

    @Test
    fun parsesMessageWithJsonBody() {
        val raw = "MESSAGE\ndestination:/topic/users/42/tasks\nsubscription:tasks\nmessage-id:7\n" +
            "content-type:application/json\n\n{\"type\":\"TASK_UPDATED\",\"taskId\":\"t1\"}\u0000"
        val frame = StompFrame.parse(raw)!!
        assertEquals("MESSAGE", frame.command)
        assertEquals("/topic/users/42/tasks", frame.headers["destination"])
        assertEquals("{\"type\":\"TASK_UPDATED\",\"taskId\":\"t1\"}", frame.body)
    }

    @Test
    fun parsesErrorFrameWithWindowsLineEndings() {
        val frame = StompFrame.parse("ERROR\r\nmessage:bad token\r\n\n\u0000")!!
        assertEquals("ERROR", frame.command)
        assertEquals("bad token", frame.headers["message"])
    }

    @Test
    fun ignoresHeartbeatsAndGarbage() {
        assertNull(StompFrame.parse("\n"))
        assertNull(StompFrame.parse(""))
        assertNull(StompFrame.parse("\r\n\n"))
    }

    @Test
    fun webSocketUrlSwitchesSchemeAndAddsPath() {
        assertEquals("wss://todoflow.ddns.net/ws/websocket", RealtimeClient.webSocketUrl("https://todoflow.ddns.net/"))
        assertEquals("ws://10.0.2.2:8080/ws/websocket", RealtimeClient.webSocketUrl("http://10.0.2.2:8080/"))
        assertEquals("wss://host/app/ws/websocket", RealtimeClient.webSocketUrl("https://host/app"))
    }
}
