package com.example.metflix.data.network.torbox

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorBoxClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "TorBoxClient"
        private const val BASE_URL = "https://api.torbox.app/v1/api"
        private const val APP_NAME = "MetFlix"
        private const val USER_AGENT = "MetFlix/1.0 (Android TV)"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Step 1 of Device Code Flow:
     * Request a new device code & user verification code to display on Android TV.
     */
    suspend fun startDeviceAuth(): TorBoxDeviceAuth? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/user/auth/device/start?app=$APP_NAME")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Device auth start failed HTTP ${resp.code}: $body")
                    return@withContext null
                }

                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext null

                TorBoxDeviceAuth(
                    deviceCode = data.optString("device_code"),
                    userCode = data.optString("code"),
                    interval = data.optInt("interval", 5),
                    expiresIn = data.optInt("expires_in", 600),
                    verificationUrl = data.optString("verification_url", "https://torbox.app/oauth/device"),
                    verificationUrlComplete = data.optString(
                        "verification_url_complete",
                        "https://torbox.app/oauth/device?app=$APP_NAME&code=${data.optString("code")}"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting device auth: ${e.message}")
            null
        }
    }

    /**
     * Step 2 of Device Code Flow:
     * Poll TorBox until the user authorizes the code on their browser.
     */
    suspend fun pollDeviceToken(deviceCode: String): TorBoxTokenResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("device_code", deviceCode).toString()
            val req = Request.Builder()
                .url("$BASE_URL/user/auth/device/token")
                .header("User-Agent", USER_AGENT)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }

                val success = json.optBoolean("success", false)
                val errorCode = json.optString("error")
                val detail = json.optString("detail")

                when {
                    resp.isSuccessful && success -> {
                        val dataObj = json.opt("data")
                        var token = when (dataObj) {
                            is JSONObject -> {
                                dataObj.optString("token").ifBlank {
                                    dataObj.optString("session_token").ifBlank {
                                        dataObj.optString("access_token").ifBlank {
                                            dataObj.optString("auth_token").ifBlank {
                                                dataObj.optString("api_key")
                                            }
                                        }
                                    }
                                }
                            }
                            is String -> dataObj.trim()
                            else -> ""
                        }

                        // In case token was returned as a stringified JSON object or quoted string
                        token = token.trim().removeSurrounding("\"")
                        if (token.startsWith("{") && token.endsWith("}")) {
                            try {
                                val inner = JSONObject(token)
                                token = inner.optString("token").ifBlank {
                                    inner.optString("session_token").ifBlank {
                                        inner.optString("access_token").ifBlank {
                                            inner.optString("api_key").ifBlank { token }
                                        }
                                    }
                                }.trim().removeSurrounding("\"")
                            } catch (_: Exception) {}
                        }

                        if (token.isNotBlank()) {
                            TorBoxTokenResult.Success(token)
                        } else {
                            TorBoxTokenResult.Error("Empty token received")
                        }
                    }
                    errorCode.equals("DEVICE_CODE_NOT_USED", ignoreCase = true) -> {
                        TorBoxTokenResult.Pending
                    }
                    errorCode.equals("ITEM_NOT_FOUND", ignoreCase = true) ||
                            errorCode.equals("EXPIRED", ignoreCase = true) -> {
                        TorBoxTokenResult.Expired
                    }
                    else -> {
                        TorBoxTokenResult.Error(if (detail.isNotBlank()) detail else "HTTP ${resp.code}: $errorCode")
                    }
                }
            }
        } catch (e: Exception) {
            TorBoxTokenResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Fetches current account status, plan level, and subscription days.
     */
    suspend fun getUserInfo(apiKey: String): TorBoxUser? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext null

        try {
            val req = Request.Builder()
                .url("$BASE_URL/user/me")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null

                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) return@withContext null
                val data = json.optJSONObject("data") ?: return@withContext null

                val id = data.optString("id", "")
                val email = data.optString("email", id)
                val plan = data.optInt("plan", 0)
                val isPremium = plan > 0
                val expiresAtStr = data.optString("premium_expires_at")

                var daysRemaining = 0
                if (!expiresAtStr.isNullOrBlank()) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        val expireDate = sdf.parse(expiresAtStr)
                        if (expireDate != null) {
                            val diffMs = expireDate.time - System.currentTimeMillis()
                            daysRemaining = (diffMs / (1000L * 86400L)).coerceAtLeast(0L).toInt()
                        }
                    } catch (_: Exception) {
                        if (isPremium) daysRemaining = 30
                    }
                } else if (isPremium) {
                    daysRemaining = 30
                }

                val planName = when (plan) {
                    1 -> "Essential"
                    2 -> "Pro"
                    3 -> "Standard"
                    else -> "Free Tier"
                }

                TorBoxUser(
                    id = id,
                    email = email,
                    plan = plan,
                    planName = planName,
                    isPremium = isPremium,
                    daysRemaining = daysRemaining,
                    customerId = data.optString("customer_id", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TorBox user info: ${e.message}")
            null
        }
    }

    /**
     * Fetches user bandwidth and usage stats.
     */
    suspend fun getUserStats(apiKey: String): TorBoxUserStats? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext null

        try {
            val req = Request.Builder()
                .url("$BASE_URL/user/stats")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null

                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext null

                val dlBytes = data.optLong("total_downloaded", 0L)
                val egressBytes = data.optLong("total_egress", 0L)
                val slots = data.optInt("active_slots", 0)

                fun formatBytes(bytes: Long): String {
                    if (bytes <= 0) return "0 GB"
                    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    return String.format(Locale.US, "%.1f GB", gb)
                }

                TorBoxUserStats(
                    totalDownloadedBytes = dlBytes,
                    totalDownloadedFormatted = formatBytes(dlBytes),
                    totalEgressBytes = egressBytes,
                    totalEgressFormatted = formatBytes(egressBytes),
                    activeSlots = slots
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user stats: ${e.message}")
            null
        }
    }

    /**
     * Directly checks if torrent info-hashes are cached on TorBox.
     */
    suspend fun checkCachedTorrents(apiKey: String, hashes: List<String>): List<TorBoxCachedItem> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || hashes.isEmpty()) return@withContext emptyList()

        try {
            val queryParams = hashes.joinToString("&") { "hash=$it" } + "&format=list&list_files=true"
            val req = Request.Builder()
                .url("$BASE_URL/torrents/checkcached?$queryParams")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext emptyList()

                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) return@withContext emptyList()

                val items = mutableListOf<TorBoxCachedItem>()
                val data = json.opt("data")

                if (data is JSONArray) {
                    for (i in 0 until data.length()) {
                        val itemObj = data.optJSONObject(i) ?: continue
                        val hash = itemObj.optString("hash")
                        val name = itemObj.optString("name")
                        val size = itemObj.optLong("size", 0L)

                        val filesList = mutableListOf<TorBoxCachedFile>()
                        val filesArr = itemObj.optJSONArray("files")
                        if (filesArr != null) {
                            for (j in 0 until filesArr.length()) {
                                val fObj = filesArr.optJSONObject(j) ?: continue
                                filesList.add(
                                    TorBoxCachedFile(
                                        id = fObj.optInt("id", j),
                                        name = fObj.optString("name"),
                                        size = fObj.optLong("size", 0L)
                                    )
                                )
                            }
                        }

                        items.add(
                            TorBoxCachedItem(
                                hash = hash,
                                isCached = true,
                                name = name,
                                size = size,
                                files = filesList
                            )
                        )
                    }
                } else if (data is JSONObject) {
                    // Object format: { "<hash>": { "name": ..., "size": ..., "files": [...] } } or { "<hash>": true }
                    val keys = data.keys()
                    while (keys.hasNext()) {
                        val hash = keys.next()
                        val itemObj = data.optJSONObject(hash)
                        if (itemObj != null) {
                            val filesList = mutableListOf<TorBoxCachedFile>()
                            val filesArr = itemObj.optJSONArray("files")
                            if (filesArr != null) {
                                for (j in 0 until filesArr.length()) {
                                    val fObj = filesArr.optJSONObject(j) ?: continue
                                    filesList.add(
                                        TorBoxCachedFile(
                                            id = fObj.optInt("id", j),
                                            name = fObj.optString("name"),
                                            size = fObj.optLong("size", 0L)
                                        )
                                    )
                                }
                            }
                            items.add(
                                TorBoxCachedItem(
                                    hash = hash,
                                    isCached = true,
                                    name = itemObj.optString("name"),
                                    size = itemObj.optLong("size", 0L),
                                    files = filesList
                                )
                            )
                        } else if (data.optBoolean(hash, false)) {
                            items.add(
                                TorBoxCachedItem(
                                    hash = hash,
                                    isCached = true
                                )
                            )
                        }
                    }
                }

                items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TorBox cache: ${e.message}")
            emptyList()
        }
    }

    /**
     * Adds a magnet link directly to TorBox cloud storage.
     * If already cached, it finishes instantly.
     */
    suspend fun createTorrent(apiKey: String, magnet: String): Int? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || magnet.isBlank()) return@withContext null

        try {
            val formBody = FormBody.Builder()
                .add("magnet", magnet)
                .add("seed", "1")
                .add("allow_zip", "false")
                .build()

            val req = Request.Builder()
                .url("$BASE_URL/torrents/createtorrent")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Create torrent failed HTTP ${resp.code}: $body")
                    return@withContext null
                }

                val json = JSONObject(body)
                val dataObj = json.opt("data")
                val torrentId = when (dataObj) {
                    is JSONObject -> dataObj.optInt("torrent_id", -1).takeIf { it > 0 }
                        ?: dataObj.optString("torrent_id").toIntOrNull()
                        ?: dataObj.optInt("id", -1).takeIf { it > 0 }
                    is Number -> dataObj.toInt().takeIf { it > 0 }
                    is String -> dataObj.toIntOrNull()?.takeIf { it > 0 }
                    else -> null
                }
                if (torrentId != null && torrentId > 0) torrentId else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TorBox torrent: ${e.message}")
            null
        }
    }

    /**
     * Obtains the direct, unthrottled high-speed CDN download/streaming link for a torrent file.
     */
    suspend fun requestDownloadLink(apiKey: String, torrentId: Int, fileId: Int? = null): String? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || torrentId <= 0) return@withContext null

        try {
            val fileParam = if (fileId != null && fileId >= 0) "&file_id=$fileId" else ""
            val req = Request.Builder()
                .url("$BASE_URL/torrents/requestdl?token=$cleanKey&torrent_id=$torrentId$fileParam&redirect=false")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null

                val json = JSONObject(body)
                val dataObj = json.opt("data")
                when (dataObj) {
                    is String -> if (dataObj.isNotBlank() && dataObj != "null") dataObj else null
                    is JSONObject -> dataObj.optString("url").takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting download link: ${e.message}")
            null
        }
    }

    /**
     * Creates an adaptive HLS stream with server-side transcoding for low-end devices.
     */
    suspend fun createStream(apiKey: String, torrentId: Int, fileId: Int? = null): TorBoxStreamResponse? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || torrentId <= 0) return@withContext null

        try {
            val fileParam = if (fileId != null && fileId >= 0) "&file_id=$fileId" else ""
            val req = Request.Builder()
                .url("$BASE_URL/stream/createstream?id=$torrentId$fileParam&type=torrent")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null

                val json = JSONObject(body)
                val dataObj = json.opt("data")
                val streamUrl = when (dataObj) {
                    is String -> dataObj.takeIf { it.isNotBlank() && it != "null" }
                    is JSONObject -> dataObj.optString("url").takeIf { it.isNotBlank() }
                    else -> null
                } ?: return@withContext null

                val quality = if (dataObj is JSONObject) dataObj.optString("quality", "1080p") else "1080p"

                TorBoxStreamResponse(
                    streamUrl = streamUrl,
                    isHls = streamUrl.contains(".m3u8"),
                    quality = quality
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating stream: ${e.message}")
            null
        }
    }

    /**
     * Lists all torrents currently saved in the user's TorBox account.
     */
    suspend fun getUserTorrents(apiKey: String): List<TorBoxCachedItem> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext emptyList()

        try {
            val req = Request.Builder()
                .url("$BASE_URL/torrents/mylist")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext emptyList()

                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                val items = mutableListOf<TorBoxCachedItem>()

                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val hash = obj.optString("hash")
                    val name = obj.optString("name")
                    val size = obj.optLong("size", 0L)
                    val filesArr = obj.optJSONArray("files")
                    val filesList = mutableListOf<TorBoxCachedFile>()
                    if (filesArr != null) {
                        for (j in 0 until filesArr.length()) {
                            val f = filesArr.optJSONObject(j) ?: continue
                            filesList.add(
                                TorBoxCachedFile(
                                    id = f.optInt("id", j),
                                    name = f.optString("name"),
                                    size = f.optLong("size", 0L)
                                )
                            )
                        }
                    }

                    val rawProgress = obj.optDouble("progress", 1.0).toFloat()
                    val downloadState = obj.optString("download_state", if (obj.optBoolean("cached", true)) "cached" else "downloading")

                    items.add(
                        TorBoxCachedItem(
                            id = obj.optInt("id", 0),
                            hash = hash,
                            isCached = obj.optBoolean("cached", true),
                            name = name,
                            size = size,
                            progress = rawProgress,
                            downloadState = downloadState,
                            files = filesList
                        )
                    )
                }
                items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user torrents: ${e.message}")
            emptyList()
        }
    }

    /**
     * Controls or deletes a torrent from the user's TorBox account.
     */
    suspend fun controlTorrent(apiKey: String, torrentId: Int, operation: String = "delete"): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || torrentId <= 0) return@withContext false

        try {
            val jsonPayload = JSONObject().apply {
                put("torrent_id", torrentId)
                put("operation", operation)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val req = Request.Builder()
                .url("$BASE_URL/torrents/controltorrent")
                .header("Authorization", "Bearer $cleanKey")
                .header("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling torrent $torrentId: ${e.message}")
            false
        }
    }
}

