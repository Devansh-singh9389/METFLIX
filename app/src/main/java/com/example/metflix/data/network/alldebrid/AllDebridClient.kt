package com.example.metflix.data.network.alldebrid

import android.util.Log
import com.example.metflix.data.settings.DebridProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.metflix.data.network.torbox.TorBoxClient
import javax.inject.Inject
import javax.inject.Singleton

data class AllDebridUserStatus(
    val isValid: Boolean,
    val provider: DebridProviderType = DebridProviderType.ALLDEBRID,
    val username: String = "",
    val isPremium: Boolean = false,
    val daysRemaining: Int = 0,
    val message: String = ""
)

@Singleton
class AllDebridClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val torBoxClient: TorBoxClient
) {
    companion object {
        private const val TAG = "DebridClient"
        private const val ALLDEBRID_API_BASE = "https://api.alldebrid.com/v4"
    }

    suspend fun checkStatus(provider: DebridProviderType, apiKey: String): AllDebridUserStatus {
        return when (provider) {
            DebridProviderType.ALLDEBRID -> checkUserStatus(apiKey)
            DebridProviderType.TORBOX -> checkTorBoxStatus(apiKey)
        }
    }

    suspend fun checkUserStatus(apiKey: String): AllDebridUserStatus = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AllDebridUserStatus(
                isValid = false,
                provider = DebridProviderType.ALLDEBRID,
                message = "API Key is empty"
            )
        }

        try {
            val req = Request.Builder()
                .url("$ALLDEBRID_API_BASE/user?agent=MetFlix&apikey=$cleanKey")
                .header("User-Agent", "MetFlix/1.0 (Android TV)")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AllDebridUserStatus(
                        isValid = false,
                        provider = DebridProviderType.ALLDEBRID,
                        message = "HTTP ${resp.code}: Unable to reach AllDebrid"
                    )
                }

                val json = JSONObject(bodyStr)
                val status = json.optString("status")

                if (status.equals("success", ignoreCase = true)) {
                    val data = json.optJSONObject("data")
                    val user = data?.optJSONObject("user")
                    val username = user?.optString("username").orEmpty()
                    val isPremium = user?.optBoolean("isPremium", false) ?: false
                    val premiumUntil = user?.optLong("premiumUntil", 0L) ?: 0L

                    val currentTimeSec = System.currentTimeMillis() / 1000L
                    val secondsRemaining = (premiumUntil - currentTimeSec).coerceAtLeast(0L)
                    val daysRemaining = (secondsRemaining / 86400L).toInt()

                    val msg = if (isPremium) {
                        "Active (Premium: $daysRemaining days left)"
                    } else {
                        "Free tier / Expired"
                    }

                    Log.d(TAG, "AllDebrid status for $username: $msg")
                    return@withContext AllDebridUserStatus(
                        isValid = true,
                        provider = DebridProviderType.ALLDEBRID,
                        username = username,
                        isPremium = isPremium,
                        daysRemaining = daysRemaining,
                        message = msg
                    )
                } else {
                    val errorObj = json.optJSONObject("error")
                    val errorMsg = errorObj?.optString("message") ?: "Invalid API Key"
                    return@withContext AllDebridUserStatus(
                        isValid = false,
                        provider = DebridProviderType.ALLDEBRID,
                        message = errorMsg
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking AllDebrid status: ${e.message}")
            AllDebridUserStatus(
                isValid = false,
                provider = DebridProviderType.ALLDEBRID,
                message = e.message ?: "Connection error"
            )
        }
    }

    suspend fun checkTorBoxStatus(apiKey: String): AllDebridUserStatus = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AllDebridUserStatus(
                isValid = false,
                provider = DebridProviderType.TORBOX,
                message = "TorBox API Key is empty"
            )
        }

        try {
            val user = torBoxClient.getUserInfo(cleanKey)
            if (user != null) {
                val msg = if (user.isPremium) {
                    "Active (Plan: ${user.planName} • ${user.daysRemaining} days left)"
                } else {
                    "Free Tier (Paid plan required for direct debrid streams)"
                }

                Log.d(TAG, "TorBox status for ${user.email}: $msg")
                AllDebridUserStatus(
                    isValid = true,
                    provider = DebridProviderType.TORBOX,
                    username = user.email,
                    isPremium = user.isPremium,
                    daysRemaining = user.daysRemaining,
                    message = msg
                )
            } else {
                AllDebridUserStatus(
                    isValid = false,
                    provider = DebridProviderType.TORBOX,
                    message = "Invalid TorBox API key or account not found"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TorBox status: ${e.message}")
            AllDebridUserStatus(
                isValid = false,
                provider = DebridProviderType.TORBOX,
                message = e.message ?: "Connection error"
            )
        }
    }
}
