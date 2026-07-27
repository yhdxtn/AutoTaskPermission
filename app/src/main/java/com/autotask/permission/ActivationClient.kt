package com.autotask.permission

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ActivationClient(private val baseUrl: String) {

    fun activate(code: String, deviceId: String, deviceName: String): ActivationResult =
        post("/api/activation/activate", code, deviceId, deviceName)

    fun verify(code: String, deviceId: String, deviceName: String): ActivationResult =
        post("/api/activation/verify", code, deviceId, deviceName)

    private fun post(path: String, code: String, deviceId: String, deviceName: String): ActivationResult {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        val payload = JSONObject()
            .put("code", code)
            .put("deviceId", deviceId)
            .put("deviceName", deviceName)
            .toString()

        connection.outputStream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
        }

        val statusCode = connection.responseCode
        val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()

        val json = if (body.isBlank()) JSONObject() else JSONObject(body)
        return ActivationResult(
            success = json.optBoolean("success", statusCode in 200..299),
            message = json.optString("message", if (statusCode in 200..299) "验证成功" else "激活失败"),
            code = json.optString("code").takeIf { it.isNotBlank() },
            deviceId = json.optString("deviceId").takeIf { it.isNotBlank() },
            expiresAt = json.optString("expiresAt").takeIf { it.isNotBlank() }
        )
    }
}

data class ActivationResult(
    val success: Boolean,
    val message: String,
    val code: String?,
    val deviceId: String?,
    val expiresAt: String?
)
