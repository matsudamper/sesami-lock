package net.matsudamper.sesamilock.sesame.cloud

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class HttpRequest(
    val method: String,
    val url: URL,
    val headers: Map<String, String>,
    val body: String?,
) {
    override fun toString(): String = "HttpRequest($method ${url.host})"
}

internal sealed interface HttpResult {
    data class Response(val statusCode: Int, val body: String) : HttpResult

    data object NotConnected : HttpResult

    data object TimedOut : HttpResult

    data object Interrupted : HttpResult
}

internal fun interface HttpExecutor {
    suspend fun execute(request: HttpRequest): HttpResult
}

internal class UrlConnectionHttpExecutor(
    private val connectTimeout: Duration,
    private val readTimeout: Duration,
) : HttpExecutor {
    override suspend fun execute(request: HttpRequest): HttpResult = runInterruptible(Dispatchers.IO) {
        val connection = request.url.openConnection() as? HttpsURLConnection ?: return@runInterruptible HttpResult.NotConnected
        try {
            connection.requestMethod = request.method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = connectTimeout.inWholeMilliseconds.toInt()
            connection.readTimeout = readTimeout.inWholeMilliseconds.toInt()
            connection.doOutput = request.body != null
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                connection.connect()
            } catch (_: ConnectException) {
                return@runInterruptible HttpResult.NotConnected
            } catch (_: UnknownHostException) {
                return@runInterruptible HttpResult.NotConnected
            }
            if (request.body != null) {
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            HttpResult.Response(status, body)
        } catch (_: SocketTimeoutException) {
            HttpResult.TimedOut
        } catch (_: IOException) {
            HttpResult.Interrupted
        } finally {
            connection.disconnect()
        }
    }
}
