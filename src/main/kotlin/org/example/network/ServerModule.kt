package org.example.network

import io.ktor.http.*
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.request.receiveMultipart
import io.ktor.http.content.PartData
import io.ktor.http.content.PartData.FileItem
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR
import org.bytedeco.opencv.global.opencv_imgcodecs.imdecode
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.javacpp.BytePointer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import org.example.DatabaseService
import org.example.FaceRecognitionService

// DTOs (Unchanged)
data class UserDto(val id: Int, val name: String, val role: String)
data class LogDto(val name: String, val role: String, val timestamp: String, val confidence: Double)
data class BoxDto(val x: Int, val y: Int, val width: Int, val height: Int)
data class RecognitionDto(val name: String, val confidence: Double, val box: BoxDto)

fun Application.serverModule() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
    }
    install(ContentNegotiation) { gson { setPrettyPrinting() } }
    install(WebSockets)

    val databaseService: DatabaseService = ServiceRegistry.databaseService
        ?: error("DatabaseService not registered")
    val recognitionService: FaceRecognitionService = ServiceRegistry.recognitionService
        ?: error("FaceRecognitionService not registered")

    routing {
        staticResources("/", "static")

        get("/camera/live.mjpeg") {
            try { // --- FIX: Start of try-catch block
                call.response.headers.append(HttpHeaders.ContentType, "multipart/x-mixed-replace; boundary=frame")
                call.respondOutputStream {
                    while (true) {
                        val frameBytes = SharedFrameHolder.getFrame()
                        if (frameBytes != null) {
                            write("--frame\r\n".toByteArray())
                            write("Content-Type: image/jpeg\r\n".toByteArray())
                            write("Content-Length: ${frameBytes.size}\r\n".toByteArray())
                            write("\r\n".toByteArray())
                            write(frameBytes)
                            write("\r\n".toByteArray())
                            flush()
                        }
                        delay(1000 / 30)
                    }
                }
            } catch (e: CancellationException) {
                // This is expected when the client disconnects or server shuts down.
                // We catch it to prevent it from being logged as an error.
                println("MJPEG stream was cancelled by client or server shutdown. This is normal.")
            } catch (e: Exception) {
                // Log other, unexpected errors
                println("An unexpected error occurred in the MJPEG stream: ${e.message}")
            } // --- FIX: End of try-catch block
        }

        get("/status") {
            call.respond(mapOf("status" to "running"))
        }
        get("/users") {
            val users = databaseService.users
            val dtos = users.map { UserDto(it.id, it.name, it.role) }
            call.respond(dtos)
        }
        get("/logs") {
            val logs = databaseService.history
            val dtos = logs.map { LogDto(it.name, it.role, it.timestamp, it.confidence) }
            call.respond(dtos)
        }
        post("/recognize") {
            // This endpoint doesn't need a fix as it doesn't loop forever.
            val multipart = call.receiveMultipart()
            var bytes: ByteArray? = null
            while (true) {
                val part = multipart.readPart() ?: break
                if (part is FileItem) {
                    val provider = try { part::class.java.getMethod("getStreamProvider").invoke(part) as java.util.function.Supplier<java.io.InputStream> } catch (_: Throwable) { null }
                    val input = provider?.get()
                    if (input != null) {
                        bytes = input.readAllBytes()
                    }
                }
                part.dispose()
            }
            val data = bytes ?: return@post call.respond(mapOf("error" to "file part missing"))
            if (data.size > 10 * 1024 * 1024) { // 10MB guard
                return@post call.respond(mapOf("error" to "file too large"))
            }
            val buf = BytePointer(*data)
            val raw = Mat(buf)
            val mat = imdecode(raw, IMREAD_COLOR)
            if (mat == null || mat.empty()) {
                return@post call.respond(mapOf("error" to "invalid image data"))
            }
            val results = recognitionService.recognizeFaces(mat)
            val payload = results.map {
                RecognitionDto(
                    name = it.name,
                    confidence = it.confidence,
                    box = BoxDto(it.faceRect.x(), it.faceRect.y(), it.faceRect.width(), it.faceRect.height())
                )
            }
            call.respond(payload)
        }
        webSocket("/events/live") {
            val channel = Channel<String>(capacity = 64)
            EventBus.register(channel)
            try { // --- FIX: Start of try-catch block
                for (msg in channel) {
                    send(Frame.Text(msg))
                }
            } catch (e: CancellationException) {
                // This is expected when the client disconnects or the server shuts down.
                // Catch it silently.
            } finally {
                // This will now run cleanly on shutdown.
                EventBus.unregister(channel)
            } // --- FIX: End of try-catch block
        }
    }
}