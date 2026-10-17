package dev.penned.app.data.update

import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 极简 HTTP/1.1 假服务（**仅测试用**）。
 *
 * 为什么不用 JDK 自带的 `com.sun.net.httpserver`：AGP 给 Kotlin 单测编译传 `-no-jdk`，类型只从
 * android.jar 解析，`com.sun.*` 编译期不可见（运行期有也没用）。自己拿 `ServerSocket` 起一个更省事，
 * 顺带能演"响应传一半就断开"这种真实场景（M2 S3 的断点续传测试要）。
 *
 * 支持：按路径注册响应、记录请求（路径 + 头 + **请求体**）、`Range: bytes=N-` 部分响应（206）、
 * 以及"写 N 字节后断开"（客户端会读到提前结束 → 可测续传）。
 *
 * **请求体**（M4-2 扩，M4-6 用来断外发边界）：按 `Content-Length` 读全，记进 [Request.body]——
 * 之前只记 path + headers，断言不了"发出去的 JSON 里有什么/没什么"。
 */
class FakeHttpServer {

    class Request(val path: String, val headers: Map<String, String>, val body: String = "")

    class Response(
        val status: Int = 200,
        val body: ByteArray = ByteArray(0),
        val headers: Map<String, String> = emptyMap(),
        /** 只写这么多字节就断开连接（测断点续传）；-1 = 正常写完。 */
        val cutAfterBytes: Int = -1,
    )

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val routes = HashMap<String, (Request) -> Response>()
    private val requestLog = CopyOnWriteArrayList<Request>()

    @Volatile
    private var running = true

    val port: Int get() = socket.localPort
    val requests: List<Request> get() = requestLog

    fun url(path: String) = "http://127.0.0.1:$port$path"

    fun on(path: String, handler: (Request) -> Response) {
        routes[path] = handler
    }

    fun on(path: String, status: Int = 200, body: String = "", headers: Map<String, String> = emptyMap()) {
        on(path) { Response(status, body.toByteArray(), headers) }
    }

    /** 像静态文件那样服务一个字节数组：支持 Range（206），可要求中途断开。 */
    fun onFile(path: String, content: ByteArray, cutAfterBytes: Int = -1) {
        on(path) { request ->
            val range = request.headers["range"]
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                val from = start.coerceIn(0, content.size)
                Response(
                    status = 206,
                    body = content.copyOfRange(from, content.size),
                    headers = mapOf(
                        "Accept-Ranges" to "bytes",
                        "Content-Range" to "bytes $from-${content.size - 1}/${content.size}",
                    ),
                    cutAfterBytes = cutAfterBytes,
                )
            } else {
                Response(
                    status = 200,
                    body = content,
                    headers = mapOf("Accept-Ranges" to "bytes"),
                    cutAfterBytes = cutAfterBytes,
                )
            }
        }
    }

    fun start() {
        Thread {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break // stop() 关掉监听
                }
                Thread { serve(client) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        try {
            socket.close()
        } catch (_: Exception) {
            // 已关
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { connection ->
                val input = connection.getInputStream().buffered()
                val requestLine = readLine(input) ?: return
                val path = requestLine.split(" ").getOrElse(1) { "/" }
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                    }
                }
                // 请求体：按 Content-Length 读全（POST /chat/completions 这类要断"发了什么"）
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val bytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(bytes, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(bytes, 0, read, Charsets.UTF_8)
                } else {
                    ""
                }
                val request = Request(path, headers, body)
                requestLog += request

                val response = routes[path]?.invoke(request)
                    ?: Response(status = 404, body = "not found".toByteArray())
                val out = BufferedOutputStream(connection.getOutputStream())
                writeResponse(out, response)
                out.flush()
            }
        } catch (_: Exception) {
            // 客户端提前断开等：测试里无所谓
        }
    }

    private fun writeResponse(out: BufferedOutputStream, response: Response) {
        val statusText = when (response.status) {
            200 -> "OK"
            206 -> "Partial Content"
            302 -> "Found"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val head = buildString {
            append("HTTP/1.1 ${response.status} $statusText\r\n")
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: ${response.body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray())
        val toWrite = if (response.cutAfterBytes >= 0) {
            minOf(response.cutAfterBytes, response.body.size)
        } else {
            response.body.size
        }
        out.write(response.body, 0, toWrite)
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().removeSuffix("\r")
            builder.append(byte.toChar())
        }
    }
}
