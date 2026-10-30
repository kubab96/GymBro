package pl.gymbro.net

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Malutki serwer HTTP nasłuchujący wyłącznie na pętli zwrotnej telefonu.
 *
 * Istnieje po to, żeby zegarek mógł zapisywać serie. Mini-aplikacja Zepp OS nie
 * umie rozmawiać wprost z inną aplikacją na telefonie - umie tylko oddać robotę
 * swojej usłudze towarzyszącej w aplikacji Zepp, a ta potrafi wykonać zwykłe
 * żądanie HTTP. Adres `127.0.0.1` nigdy nie opuszcza telefonu, więc działa to
 * na siłowni bez zasięgu i bez żadnego serwera w internecie.
 *
 * Gniazdo wiążemy jawnie z adresem pętli zwrotnej, a nie z `0.0.0.0`. Inaczej
 * każdy w tej samej sieci Wi-Fi mógłby dopisywać serie do cudzego treningu.
 *
 * Nie jest to serwer ogólnego przeznaczenia: obsługuje jedno żądanie naraz,
 * bo klient jest jeden i żądania są malutkie. Limit czasu na odczyt pilnuje,
 * żeby zawieszone połączenie nie zablokowało kolejnych.
 */
class LocalServer(
    private val port: Int,
    private val route: (Request) -> Response
) {
    data class Request(
        val method: String,
        val path: String,
        val token: String?,
        val body: String
    )

    data class Response(val status: Int, val json: String) {
        companion object {
            fun ok(json: String) = Response(200, json)
            fun error(status: Int, message: String) =
                Response(status, """{"error":"$message"}""")
        }
    }

    @Volatile
    private var socket: ServerSocket? = null

    @Volatile
    private var running = false

    /** @return true, gdy udało się zająć port. */
    fun start(): Boolean {
        if (running) return true
        return try {
            socket = ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
            running = true
            thread(name = "gymbro-link", isDaemon = true) { loop() }
            Log.i(TAG, "nasłuch na 127.0.0.1:$port")
            true
        } catch (e: IOException) {
            Log.i(TAG, "nie udało się zająć portu $port: ${e.message}")
            false
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        Log.i(TAG, "zatrzymany")
    }

    private fun loop() {
        while (running) {
            val client = try {
                socket?.accept() ?: break
            } catch (e: IOException) {
                // Zamknięcie gniazda jest normalnym sposobem zatrzymania pętli.
                if (running) Log.i(TAG, "accept: ${e.message}")
                break
            }
            runCatching { serve(client) }
                .onFailure { Log.i(TAG, "obsługa połączenia: ${it.message}") }
            runCatching { client.close() }
        }
    }

    private fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) {
            write(client, Response.error(400, "zly wiersz zadania"))
            return
        }

        var length = 0
        var token: String? = null
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (name) {
                "content-length" -> length = value.toIntOrNull() ?: 0
                HEADER_TOKEN -> token = value
            }
        }

        // Ciało czytamy co do bajta z Content-Length. Czytanie „do końca strumienia"
        // wisiałoby do limitu czasu, bo klient trzyma połączenie otwarte.
        val body = if (length > 0 && length <= MAX_BODY) {
            val buf = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = try {
                    input.read(buf, read, length - read)
                } catch (e: SocketTimeoutException) {
                    -1
                }
                if (n <= 0) break
                read += n
            }
            String(buf, 0, read, Charsets.UTF_8)
        } else ""

        val response = runCatching {
            route(Request(parts[0].uppercase(), parts[1].substringBefore('?'), token, body))
        }.getOrElse {
            Log.i(TAG, "blad obslugi: ${it.message}")
            Response.error(500, "blad wewnetrzny")
        }
        write(client, response)
    }

    /** Wiersz zakończony CRLF; sam czyta bajtami, bo nagłówki są czystym ASCII. */
    private fun readLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = try {
                input.read()
            } catch (e: SocketTimeoutException) {
                return null
            }
            if (b == -1) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
            if (out.size() > MAX_LINE) return null
        }
        return out.toString("UTF-8")
    }

    private fun write(client: Socket, response: Response) {
        val bytes = response.json.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(" OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out = client.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        409 -> "Conflict"
        else -> "Internal Server Error"
    }

    companion object {
        const val HEADER_TOKEN = "x-gymbro-token"
        private const val TAG = "GymBroLink"
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_BODY = 64 * 1024
        private const val MAX_LINE = 8 * 1024
    }
}
