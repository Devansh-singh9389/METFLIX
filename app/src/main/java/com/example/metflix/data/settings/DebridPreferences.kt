package com.example.metflix.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.metflix.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

enum class DebridProviderType(val displayName: String, val id: String) {
    ALLDEBRID("AllDebrid", "alldebrid"),
    TORBOX("TorBox", "torbox")
}

@Singleton
class DebridPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DebridPreferences"
        private const val PREFS_NAME = "metflix_debrid_prefs"
        private const val KEY_SELECTED_PROVIDER = "key_selected_provider"
        private const val KEY_ALLDEBRID_API_KEY = "key_alldebrid_api_key_enc"
        private const val KEY_TORBOX_API_KEY = "key_torbox_api_key_enc"
        private const val LEGACY_KEY_ALLDEBRID = "key_alldebrid_api_key"
        private const val LEGACY_KEY_TORBOX = "key_torbox_api_key"
        private const val KEY_ALLDEBRID_ENABLED = "key_alldebrid_enabled"
        private const val KEY_USER_INFO = "key_user_info"
        private const val KEY_IS_VERIFIED = "key_is_verified"
        private const val KEY_IS_PREMIUM = "key_is_premium"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_DAYS_REMAINING = "key_days_remaining"
        private const val KEY_PLAN_NAME = "key_plan_name"
        private const val KEY_AUTO_FALLBACK = "key_auto_fallback_to_free"
        private const val KEY_PLAYBACK_SOURCE = "key_playback_source"
        private const val KEY_PLAYBACK_QUALITY = "key_playback_quality"
        private const val KEY_PLAYBACK_PREFER_HINDI = "key_playback_prefer_hindi"

        private const val KEYSTORE_ALIAS = "metflix_debrid_crypto_alias"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Hardware-backed Android KeyStore encryption for API keys
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    private fun encrypt(plainText: String): String? {
        if (plainText.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(iv.size + encryptedBytes.size)
            buffer.put(iv)
            buffer.put(encryptedBytes)
            Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Hardware KeyStore encryption failed: ${e.message}")
            null
        }
    }

    private fun decrypt(encryptedText: String): String? {
        if (encryptedText.isBlank()) return ""
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null
            val buffer = ByteBuffer.wrap(combined)
            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            buffer.get(cipherBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Hardware KeyStore decryption failed: ${e.message}")
            null
        }
    }

    /**
     * Sanitizes API keys by stripping JSON wrappers, curly braces, and quotes if present.
     */
     fun sanitizeApiKey(rawKey: String): String {
        var clean = rawKey.trim().removeSurrounding("\"")
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val json = JSONObject(clean)
                clean = json.optString("token").ifBlank {
                    json.optString("session_token").ifBlank {
                        json.optString("access_token").ifBlank {
                            json.optString("auth_token").ifBlank {
                                json.optString("api_key").ifBlank {
                                    json.optString("data").ifBlank { clean }
                                }
                            }
                        }
                    }
                }.trim().removeSurrounding("\"")
            } catch (_: Exception) {}
        }
        return clean
    }

    private fun readEncryptedKey(encKey: String, legacyKey: String, defaultFallback: String): String {
        val encValue = prefs.getString(encKey, null)
        if (!encValue.isNullOrBlank()) {
            val decrypted = decrypt(encValue)
            if (!decrypted.isNullOrBlank()) return sanitizeApiKey(decrypted)
        }
        // Check legacy plain-text key if present, then securely migrate or remove from disk
        val legacyValue = prefs.getString(legacyKey, null)
        if (!legacyValue.isNullOrBlank()) {
            val sanitized = sanitizeApiKey(legacyValue)
            val encrypted = encrypt(sanitized)
            if (encrypted != null) {
                prefs.edit().putString(encKey, encrypted).remove(legacyKey).apply()
            } else {
                // If encryption fails, remove legacy plaintext from disk to prevent exposure
                prefs.edit().remove(legacyKey).apply()
            }
            return sanitized
        }
        return sanitizeApiKey(defaultFallback)
    }

    // Provider selection
    private val initialProviderName = prefs.getString(KEY_SELECTED_PROVIDER, DebridProviderType.ALLDEBRID.name)
    private val initialProvider = try {
        DebridProviderType.valueOf(initialProviderName ?: DebridProviderType.ALLDEBRID.name)
    } catch (e: Exception) {
        DebridProviderType.ALLDEBRID
    }

    private val _selectedProvider = MutableStateFlow(initialProvider)
    val selectedProvider: StateFlow<DebridProviderType> = _selectedProvider.asStateFlow()

    // AllDebrid key
    private val initialAllDebridKey = readEncryptedKey(
        KEY_ALLDEBRID_API_KEY,
        LEGACY_KEY_ALLDEBRID,
        BuildConfig.DEFAULT_ALLDEBRID_KEY
    )
    private val _allDebridApiKey = MutableStateFlow(initialAllDebridKey)
    val allDebridApiKey: StateFlow<String> = _allDebridApiKey.asStateFlow()

    // TorBox key
    private val initialTorBoxKey = readEncryptedKey(
        KEY_TORBOX_API_KEY,
        LEGACY_KEY_TORBOX,
        BuildConfig.DEFAULT_TORBOX_KEY
    )
    private val _torBoxApiKey = MutableStateFlow(initialTorBoxKey)
    val torBoxApiKey: StateFlow<String> = _torBoxApiKey.asStateFlow()

    // Provider-specific verification key helpers
    private fun getVerifiedKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_IS_VERIFIED"
    private fun getPremiumKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_IS_PREMIUM"
    private fun getEmailKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_USER_EMAIL"
    private fun getDaysKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_DAYS_REMAINING"
    private fun getPlanKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_PLAN_NAME"
    private fun getUserInfoKey(provider: DebridProviderType) = "${provider.name.lowercase()}_$KEY_USER_INFO"

    // Active key (for currently selected provider)
    private val _apiKey = MutableStateFlow(
        if (initialProvider == DebridProviderType.ALLDEBRID) initialAllDebridKey else initialTorBoxKey
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALLDEBRID_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _userInfo = MutableStateFlow(
        prefs.getString(getUserInfoKey(initialProvider), null) ?: prefs.getString(KEY_USER_INFO, "").orEmpty()
    )
    val userInfo: StateFlow<String> = _userInfo.asStateFlow()

    private val _isVerified = MutableStateFlow(
        if (prefs.contains(getVerifiedKey(initialProvider))) {
            prefs.getBoolean(getVerifiedKey(initialProvider), false)
        } else {
            prefs.getBoolean(KEY_IS_VERIFIED, false)
        }
    )
    val isVerified: StateFlow<Boolean> = _isVerified.asStateFlow()

    private val _isPremium = MutableStateFlow(
        if (prefs.contains(getPremiumKey(initialProvider))) {
            prefs.getBoolean(getPremiumKey(initialProvider), false)
        } else {
            prefs.getBoolean(KEY_IS_PREMIUM, false)
        }
    )
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _userEmail = MutableStateFlow(
        prefs.getString(getEmailKey(initialProvider), null) ?: prefs.getString(KEY_USER_EMAIL, "").orEmpty()
    )
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _daysRemaining = MutableStateFlow(
        if (prefs.contains(getDaysKey(initialProvider))) {
            prefs.getInt(getDaysKey(initialProvider), 0)
        } else {
            prefs.getInt(KEY_DAYS_REMAINING, 0)
        }
    )
    val daysRemaining: StateFlow<Int> = _daysRemaining.asStateFlow()

    private val _planName = MutableStateFlow(
        prefs.getString(getPlanKey(initialProvider), null) ?: prefs.getString(KEY_PLAN_NAME, "").orEmpty()
    )
    val planName: StateFlow<String> = _planName.asStateFlow()

    private val _autoFallbackToFree = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FALLBACK, true))
    val autoFallbackToFree: StateFlow<Boolean> = _autoFallbackToFree.asStateFlow()

    private val _keystoreError = MutableStateFlow<String?>(null)
    val keystoreError: StateFlow<String?> = _keystoreError.asStateFlow()

    private val _playbackSource = MutableStateFlow(prefs.getString(KEY_PLAYBACK_SOURCE, "debrid") ?: "debrid")
    val playbackSource: StateFlow<String> = _playbackSource.asStateFlow()

    private val _playbackQuality = MutableStateFlow(prefs.getString(KEY_PLAYBACK_QUALITY, "720p") ?: "720p")
    val playbackQuality: StateFlow<String> = _playbackQuality.asStateFlow()

    private val _playbackPreferHindi = MutableStateFlow(prefs.getBoolean(KEY_PLAYBACK_PREFER_HINDI, true))
    val playbackPreferHindi: StateFlow<Boolean> = _playbackPreferHindi.asStateFlow()

    fun loadVerificationFor(provider: DebridProviderType) {
        val verifiedKey = getVerifiedKey(provider)
        val isVerified = if (prefs.contains(verifiedKey)) {
            prefs.getBoolean(verifiedKey, false)
        } else {
            prefs.getBoolean(KEY_IS_VERIFIED, false)
        }
        val isPremium = if (prefs.contains(getPremiumKey(provider))) {
            prefs.getBoolean(getPremiumKey(provider), false)
        } else {
            prefs.getBoolean(KEY_IS_PREMIUM, false)
        }
        val email = prefs.getString(getEmailKey(provider), null) ?: prefs.getString(KEY_USER_EMAIL, "").orEmpty()
        val days = if (prefs.contains(getDaysKey(provider))) prefs.getInt(getDaysKey(provider), 0) else prefs.getInt(KEY_DAYS_REMAINING, 0)
        val plan = prefs.getString(getPlanKey(provider), null) ?: prefs.getString(KEY_PLAN_NAME, "").orEmpty()
        val info = prefs.getString(getUserInfoKey(provider), null) ?: prefs.getString(KEY_USER_INFO, "").orEmpty()

        _isVerified.value = isVerified
        _isPremium.value = isPremium
        _userEmail.value = email
        _daysRemaining.value = days
        _planName.value = plan
        _userInfo.value = info
    }

    fun savePlaybackPreferences(source: String, quality: String, preferHindi: Boolean) {
        prefs.edit()
            .putString(KEY_PLAYBACK_SOURCE, source)
            .putString(KEY_PLAYBACK_QUALITY, quality)
            .putBoolean(KEY_PLAYBACK_PREFER_HINDI, preferHindi)
            .apply()
        _playbackSource.value = source
        _playbackQuality.value = quality
        _playbackPreferHindi.value = preferHindi
    }

    fun selectProvider(provider: DebridProviderType) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name).apply()
        _selectedProvider.value = provider
        _apiKey.value = when (provider) {
            DebridProviderType.ALLDEBRID -> _allDebridApiKey.value
            DebridProviderType.TORBOX -> _torBoxApiKey.value
        }
        loadVerificationFor(provider)
    }

    fun saveApiKey(key: String) {
        val trimmed = sanitizeApiKey(key)
        if (trimmed.isBlank()) {
            when (_selectedProvider.value) {
                DebridProviderType.ALLDEBRID -> {
                    prefs.edit().remove(KEY_ALLDEBRID_API_KEY).remove(LEGACY_KEY_ALLDEBRID).apply()
                    _allDebridApiKey.value = ""
                }
                DebridProviderType.TORBOX -> {
                    prefs.edit().remove(KEY_TORBOX_API_KEY).remove(LEGACY_KEY_TORBOX).apply()
                    _torBoxApiKey.value = ""
                }
            }
            _apiKey.value = ""
            clearVerification()
            _keystoreError.value = null
            return
        }

        val encrypted = encrypt(trimmed)
        if (encrypted != null) {
            when (_selectedProvider.value) {
                DebridProviderType.ALLDEBRID -> {
                    prefs.edit().putString(KEY_ALLDEBRID_API_KEY, encrypted).remove(LEGACY_KEY_ALLDEBRID).apply()
                    _allDebridApiKey.value = trimmed
                }
                DebridProviderType.TORBOX -> {
                    prefs.edit().putString(KEY_TORBOX_API_KEY, encrypted).remove(LEGACY_KEY_TORBOX).apply()
                    _torBoxApiKey.value = trimmed
                }
            }
            _keystoreError.value = null
        } else {
            // NEVER write unencrypted secret to disk! Retain only in memory for current session
            Log.e(TAG, "Hardware KeyStore encryption failed! Retaining key in memory only to prevent plaintext exposure on disk.")
            _keystoreError.value = "Hardware encryption failed on device. Key is active in memory for this session only."
            when (_selectedProvider.value) {
                DebridProviderType.ALLDEBRID -> _allDebridApiKey.value = trimmed
                DebridProviderType.TORBOX -> _torBoxApiKey.value = trimmed
            }
        }
        _apiKey.value = trimmed
        clearVerification()
    }

    fun saveApiKeyForProvider(provider: DebridProviderType, key: String) {
        val trimmed = sanitizeApiKey(key)
        if (trimmed.isBlank()) {
            when (provider) {
                DebridProviderType.ALLDEBRID -> {
                    prefs.edit().remove(KEY_ALLDEBRID_API_KEY).remove(LEGACY_KEY_ALLDEBRID).apply()
                    _allDebridApiKey.value = ""
                }
                DebridProviderType.TORBOX -> {
                    prefs.edit().remove(KEY_TORBOX_API_KEY).remove(LEGACY_KEY_TORBOX).apply()
                    _torBoxApiKey.value = ""
                }
            }
            if (_selectedProvider.value == provider) {
                _apiKey.value = ""
                clearVerification()
            }
            return
        }

        val encrypted = encrypt(trimmed)
        if (encrypted != null) {
            when (provider) {
                DebridProviderType.ALLDEBRID -> {
                    prefs.edit().putString(KEY_ALLDEBRID_API_KEY, encrypted).remove(LEGACY_KEY_ALLDEBRID).apply()
                    _allDebridApiKey.value = trimmed
                }
                DebridProviderType.TORBOX -> {
                    prefs.edit().putString(KEY_TORBOX_API_KEY, encrypted).remove(LEGACY_KEY_TORBOX).apply()
                    _torBoxApiKey.value = trimmed
                }
            }
            _keystoreError.value = null
        } else {
            Log.e(TAG, "Hardware KeyStore encryption failed! Retaining key in memory only.")
            _keystoreError.value = "Hardware encryption failed on device. Key is active in memory for this session only."
            when (provider) {
                DebridProviderType.ALLDEBRID -> _allDebridApiKey.value = trimmed
                DebridProviderType.TORBOX -> _torBoxApiKey.value = trimmed
            }
        }
        if (_selectedProvider.value == provider) {
            _apiKey.value = trimmed
            clearVerification()
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLDEBRID_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    fun setAutoFallbackToFree(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FALLBACK, enabled).apply()
        _autoFallbackToFree.value = enabled
    }

    fun saveUserInfo(info: String, provider: DebridProviderType = _selectedProvider.value) {
        prefs.edit().putString(getUserInfoKey(provider), info).apply()
        if (_selectedProvider.value == provider) {
            _userInfo.value = info
        }
    }

    fun saveVerificationStatus(
        isVerified: Boolean,
        isPremium: Boolean,
        email: String,
        days: Int,
        plan: String,
        provider: DebridProviderType = _selectedProvider.value
    ) {
        prefs.edit()
            .putBoolean(getVerifiedKey(provider), isVerified)
            .putBoolean(getPremiumKey(provider), isPremium)
            .putString(getEmailKey(provider), email)
            .putInt(getDaysKey(provider), days)
            .putString(getPlanKey(provider), plan)
            .apply()
        if (_selectedProvider.value == provider) {
            _isVerified.value = isVerified
            _isPremium.value = isPremium
            _userEmail.value = email
            _daysRemaining.value = days
            _planName.value = plan
        }
    }

    fun clearVerification(provider: DebridProviderType = _selectedProvider.value) {
        prefs.edit()
            .putBoolean(getVerifiedKey(provider), false)
            .putBoolean(getPremiumKey(provider), false)
            .putString(getEmailKey(provider), "")
            .putInt(getDaysKey(provider), 0)
            .putString(getPlanKey(provider), "")
            .putString(getUserInfoKey(provider), "")
            .apply()
        if (_selectedProvider.value == provider) {
            _isVerified.value = false
            _isPremium.value = false
            _userEmail.value = ""
            _daysRemaining.value = 0
            _planName.value = ""
            _userInfo.value = ""
        }
    }

    fun clearApiKey() {
        saveApiKey("")
        clearVerification()
    }

    fun hasValidKey(): Boolean =
        _apiKey.value.isNotBlank() && _isEnabled.value

    fun hasVerifiedPremiumDebrid(): Boolean =
        _isEnabled.value && _isVerified.value && _isPremium.value && _apiKey.value.isNotBlank()
}
