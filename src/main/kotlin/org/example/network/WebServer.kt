package org.example.network

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.example.DatabaseService
import org.example.FaceRecognitionService

class WebServer(
    private val databaseService: DatabaseService,
    private val recognitionService: FaceRecognitionService
) {
    private var server: ApplicationEngine? = null

    fun start() {
        // Bridge Java services to the Ktor module
        ServiceRegistry.databaseService = databaseService
        ServiceRegistry.recognitionService = recognitionService

        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            serverModule()
        }.also { it.start(wait = false) }

        println("Ktor web server started on http://0.0.0.0:8080")
    }

    fun stop() {
        server?.let {
            println("Stopping Ktor web server...")
            it.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
            println("Web server stopped.")
        }
    }
}


