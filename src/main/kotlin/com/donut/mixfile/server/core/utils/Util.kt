package com.donut.mixfile.server.core.utils


import com.donut.mixfile.server.core.aes.generateRandomByteArray
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

fun String.getFileExtension(): String {
    return substringAfterLast(".", "")
}

fun String.sanitizeFileName(): String {
    // 定义非法字符，包括控制字符、文件系统非法字符、路径遍历等
    val illegalChars = "[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]".toRegex()
    // Windows 保留文件名（大小写不敏感）
    val reservedNames = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    // 处理文件名
    var sanitized = this
        // 替换非法字符为下划线
        .replace(illegalChars, "_")
        // 移除路径遍历序列
        .replace("..", "_")
        .trim()

    // 检查是否为 Windows 保留文件名
    val baseName = sanitized.substringBeforeLast(".").uppercase()
    if (baseName in reservedNames) {
        sanitized = "_$sanitized"
    }

    return sanitized.takeLast(255).ifEmpty { "unnamed_file" }
}


fun genRandomString(
    length: Int = 32,
    charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
): String {
    return (1..length)
        .map { Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}

fun isValidURL(urlString: String): Boolean {
    return try {
        val uri = URI.create(urlString)

        // 获取协议和主机名
        val protocol = uri.scheme
        val host = uri.host

        // 检查协议和主机名是否为空
        if (protocol.isNullOrBlank() || host.isNullOrBlank()) {
            return false
        }

        // 可选：限制协议类型
        protocol in listOf("http", "https")
    } catch (e: IllegalArgumentException) {
        false
    }
}


fun fileFormHeaders(
    suffix: String = ".gif",
    mimeType: String = "image/gif",
): Headers {
    return Headers.build {
        append(HttpHeaders.ContentType, mimeType)
        append(
            HttpHeaders.ContentDisposition,
            "filename=\"${genRandomString(5)}${suffix}\""
        )
    }
}


fun concurrencyLimit(
    limit: Int,
    route: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit,
): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    val tasks = CopyOnWriteArrayList<() -> Unit>()
    return route@{
        while (tasks.size > limit) {
            val remove = tasks.removeAt(0)
            ignoreError {
                remove()
            }
        }
        val cancel: () -> Unit = {
            launch {
                throw Throwable("服务器达到并发限制")
            }
        }
        tasks.add(cancel)
        route(Unit)
        tasks.remove(cancel)
    }
}

inline fun <T> ignoreError(block: () -> T): T? {
    try {
        return block()
    } catch (_: Exception) {

    }
    return null
}


fun getRandomEncKey() = generateRandomByteArray(256)

fun compressGzip(input: String): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    GZIPOutputStream(byteArrayOutputStream).use { gzip ->
        gzip.write(input.toByteArray())
    }
    return byteArrayOutputStream.toByteArray()
}

fun decompressGzip(compressed: ByteArray): String {
    val byteArrayInputStream = ByteArrayInputStream(compressed)
    GZIPInputStream(byteArrayInputStream).use { gzip ->
        return gzip.bufferedReader().use { it.readText() }
    }
}

fun String.encodeURL(): String {
    return encodeURLParameter()
}

fun String.parseFileMimeType() = ContentType.defaultForFilePath(this)

@OptIn(InternalAPI::class)
fun FormBuilder.add(key: String, value: Any?, headers: Headers = Headers.Empty) {
    append(key.quote(), value ?: "", headers)
}

class StreamContent(private val stream: InputStream, val length: Long = 0) :
    OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        stream.copyTo(channel.toOutputStream())
    }

    override val contentLength: Long
        get() = length

}

suspend fun <T> retry(
    times: Int = 3,
    delay: Long = 500,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delay)
        }
    }
    return block() // 最后一次尝试
}

fun RoutingContext.getHeader(name: String) = call.request.header(name)

fun findAvailablePort(startPort: Int = 9527, endPort: Int = 65535): Int? {
    for (port in startPort..endPort) {
        ignoreError {
            // 尝试绑定到指定端口
            ServerSocket(port).use { serverSocket ->
                // 成功绑定，返回该端口
                return serverSocket.localPort
            }
        }
    }
    return null
}