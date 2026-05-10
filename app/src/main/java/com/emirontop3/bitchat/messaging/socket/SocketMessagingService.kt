package com.emirontop3.bitchat.messaging.socket

import io.socket.client.IO
import io.socket.client.Socket

class SocketMessagingService {
    private val socket: Socket = IO.socket("https://example-socket-server.com")
    fun connect() = socket.connect()
    fun sendMessage(message: String) = socket.emit("message", message)
}
