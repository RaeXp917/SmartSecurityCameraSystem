package org.example.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import org.example.DatabaseService
import org.example.FaceRecognitionService

/**
 * A lightweight singleton registry to bridge Java-created services
 * into the Kotlin Ktor module.
 */
object ServiceRegistry {
    @Volatile var databaseService: DatabaseService? = null
    @Volatile var recognitionService: FaceRecognitionService? = null
}

/**
 * A simple, synchronized event bus to broadcast messages from the Java
 * recognition loop to any connected Kotlin WebSocket clients.
 */
object EventBus {
    private val channels = mutableSetOf<Channel<String>>()

    @Synchronized fun register(channel: Channel<String>) {
        channels.add(channel)
    }

    @Synchronized fun unregister(channel: Channel<String>) {
        channels.remove(channel)
        channel.close()
    }

    /**
     * This method is annotated with @JvmStatic so it can be easily
     * called from our Java code (e.g., EventBus.broadcast(...)).
     */
    @JvmStatic
    fun broadcast(json: String) {
        val snapshot: List<Channel<String>> = synchronized(this) { channels.toList() }
        snapshot.forEach { ch ->
            ch.trySendBlocking(json)
        }
    }
}