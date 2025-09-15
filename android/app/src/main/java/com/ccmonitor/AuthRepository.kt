package com.ccmonitor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthQRResponse(
    val success: Boolean,
    val data: QRData? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: String
)

@Serializable
data class QRData(
    val qrCode: String,
    val guestToken: String,
    val expiresIn: Int,
    val instructions: String
)

@Serializable
data class MobileAuthRequest(
    val guestToken: String,
    val deviceId: String
)

@Serializable
data class MobileAuthResponse(
    val success: Boolean,
    val data: AuthData? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: String
)

@Serializable
data class AuthData(
    val apiKey: String,
    val serverInfo: ServerInfo,
    val message: String
)

@Serializable
data class ServerInfo(
    val version: String,
    val features: List<String>,
    val protocol: String,
    val websocketUrl: String
)

@Serializable
data class ApiKeyInfo(
    val deviceId: String,
    val createdAt: String,
    val expiresAt: String,
    val lastUsed: String? = null,
    val revoked: Boolean,
    val valid: Boolean
)

@Serializable
data class ApiKeyInfoResponse(
    val success: Boolean,
    val data: ApiKeyInfo? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: String
)

class AuthRepository private constructor(
    private val context: Context,
    private val baseUrl: String = "http://localhost:3000"
) {
    private val tag = "AuthRepository"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }

    // Encrypted shared preferences for secure storage
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "cc_monitor_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to create encrypted preferences, falling back to regular preferences", e)
            context.getSharedPreferences("cc_monitor_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context, baseUrl: String = "http://localhost:3000"): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(context.applicationContext, baseUrl).also { INSTANCE = it }
            }
        }
    }

    suspend fun generateQRCode(): Result<QRData> {
        return try {
            Log.d(tag, "Requesting QR code generation")

            val response: AuthQRResponse = client.post("$baseUrl/api/auth/qr") {
                contentType(ContentType.Application.Json)
            }.body()

            if (response.success && response.data != null) {
                Log.i(tag, "QR code generated successfully")
                Result.success(response.data)
            } else {
                val error = response.error ?: response.message ?: "Unknown error"
                Log.e(tag, "QR generation failed: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to generate QR code", e)
            Result.failure(e)
        }
    }

    suspend fun authenticateWithQRToken(guestToken: String, deviceId: String): Result<AuthData> {
        return try {
            Log.d(tag, "Authenticating with guest token")

            val request = MobileAuthRequest(guestToken, deviceId)
            val response: MobileAuthResponse = client.post("$baseUrl/api/auth/mobile") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.success && response.data != null) {
                Log.i(tag, "Authentication successful")

                // Save the API key securely
                saveApiKey(response.data.apiKey, deviceId)

                Result.success(response.data)
            } else {
                val error = response.error ?: response.message ?: "Authentication failed"
                Log.e(tag, "Authentication failed: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to authenticate", e)
            Result.failure(e)
        }
    }

    suspend fun refreshApiKey(): Result<String> {
        val currentApiKey = getApiKey()
        if (currentApiKey == null) {
            return Result.failure(Exception("No API key to refresh"))
        }

        return try {
            Log.d(tag, "Refreshing API key")

            val response: MobileAuthResponse = client.post("$baseUrl/api/auth/refresh") {
                header("Authorization", "Bearer $currentApiKey")
            }.body()

            if (response.success && response.data != null) {
                Log.i(tag, "API key refreshed successfully")

                // Update stored API key
                val deviceId = getDeviceId() ?: android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                saveApiKey(response.data.apiKey, deviceId)

                Result.success(response.data.apiKey)
            } else {
                val error = response.error ?: response.message ?: "Refresh failed"
                Log.e(tag, "API key refresh failed: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh API key", e)
            Result.failure(e)
        }
    }

    suspend fun getApiKeyInfo(): Result<ApiKeyInfo> {
        val apiKey = getApiKey()
        if (apiKey == null) {
            return Result.failure(Exception("No API key available"))
        }

        return try {
            Log.d(tag, "Getting API key info")

            val response: ApiKeyInfoResponse = client.get("$baseUrl/api/auth/info") {
                header("Authorization", "Bearer $apiKey")
            }.body()

            if (response.success && response.data != null) {
                Log.i(tag, "API key info retrieved successfully")
                Result.success(response.data)
            } else {
                val error = response.error ?: response.message ?: "Failed to get API key info"
                Log.e(tag, "Failed to get API key info: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to get API key info", e)
            Result.failure(e)
        }
    }

    suspend fun revokeApiKey(): Result<Boolean> {
        val apiKey = getApiKey()
        if (apiKey == null) {
            return Result.failure(Exception("No API key to revoke"))
        }

        return try {
            Log.d(tag, "Revoking API key")

            val response: MobileAuthResponse = client.post("$baseUrl/api/auth/revoke") {
                header("Authorization", "Bearer $apiKey")
            }.body()

            if (response.success) {
                Log.i(tag, "API key revoked successfully")
                clearStoredCredentials()
                Result.success(true)
            } else {
                val error = response.error ?: response.message ?: "Revocation failed"
                Log.e(tag, "API key revocation failed: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to revoke API key", e)
            Result.failure(e)
        }
    }

    // Secure storage methods
    fun saveApiKey(apiKey: String, deviceId: String) {
        try {
            encryptedPrefs.edit()
                .putString("api_key", apiKey)
                .putString("device_id", deviceId)
                .putLong("saved_at", System.currentTimeMillis())
                .apply()
            Log.d(tag, "API key saved securely")
        } catch (e: Exception) {
            Log.e(tag, "Failed to save API key", e)
        }
    }

    fun getApiKey(): String? {
        return try {
            val apiKey = encryptedPrefs.getString("api_key", null)
            if (apiKey != null) {
                Log.d(tag, "API key retrieved from secure storage")
            }
            apiKey
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve API key", e)
            null
        }
    }

    fun getDeviceId(): String? {
        return try {
            encryptedPrefs.getString("device_id", null)
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve device ID", e)
            null
        }
    }

    fun hasValidCredentials(): Boolean {
        val apiKey = getApiKey()
        val savedAt = encryptedPrefs.getLong("saved_at", 0)
        val now = System.currentTimeMillis()

        // Check if credentials exist and are not older than 29 days (give 1 day buffer)
        return apiKey != null && (now - savedAt) < (29 * 24 * 60 * 60 * 1000)
    }

    fun clearStoredCredentials() {
        try {
            encryptedPrefs.edit()
                .remove("api_key")
                .remove("device_id")
                .remove("saved_at")
                .apply()
            Log.d(tag, "Stored credentials cleared")
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear stored credentials", e)
        }
    }

    suspend fun validateStoredCredentials(): Result<Boolean> {
        if (!hasValidCredentials()) {
            return Result.success(false)
        }

        return try {
            val result = getApiKeyInfo()
            result.map { it.valid && !it.revoked }
        } catch (e: Exception) {
            Log.e(tag, "Failed to validate stored credentials", e)
            Result.success(false)
        }
    }

    fun cleanup() {
        client.close()
        INSTANCE = null
    }
}