package com.example.firefly_go_android.network

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    companion object {
        private const val BASE_URL = "https://api.punklorde.org"
        private const val TOKEN_KEY = "access_token"
        private const val EMAIL_KEY = "user_email"
        private const val IS_PLAYER_KEY = "is_player"
    }

    var accessToken: String?
        get() = sharedPrefs.getString(TOKEN_KEY, null)
        set(value) = sharedPrefs.edit().putString(TOKEN_KEY, value).apply()

    var userEmail: String?
        get() = sharedPrefs.getString(EMAIL_KEY, null)
        set(value) = sharedPrefs.edit().putString(EMAIL_KEY, value).apply()

    var isPlayer: Boolean
        get() = sharedPrefs.getBoolean(IS_PLAYER_KEY, false)
        set(value) = sharedPrefs.edit().putBoolean(IS_PLAYER_KEY, value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null

    suspend fun login(email: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/auth/signin")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).getString("message")
                    } catch (e: Exception) {
                        "Login failed: ${response.code}"
                    }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val jsonResponse = JSONObject(bodyStr)
                if (!jsonResponse.getBoolean("status")) {
                    return@withContext Result.failure(Exception(jsonResponse.optString("message", "Login failed")))
                }

                val data = jsonResponse.getJSONObject("data")
                val token = data.getString("access_token")
                accessToken = token
                userEmail = email

                // Fetch user profile to verify role
                return@withContext fetchProfileAndVerifyRole(token)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchProfileAndVerifyRole(token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/users/current")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // Token might have expired, clear local credentials
                    if (response.code == 401) {
                        logout()
                    }
                    return@withContext Result.failure(Exception("Failed to fetch profile: ${response.code}"))
                }

                val jsonResponse = JSONObject(bodyStr)
                if (!jsonResponse.getBoolean("status")) {
                    return@withContext Result.failure(Exception(jsonResponse.optString("message", "Failed to fetch profile")))
                }

                val data = jsonResponse.getJSONObject("data")
                val rolesArray = data.getJSONArray("roles")
                var hasPlayerRole = false
                for (i in 0 until rolesArray.length()) {
                    val roleObj = rolesArray.getJSONObject(i)
                    val roleName = roleObj.getString("name").uppercase()
                    if (roleName == "PLAYER" || roleName == "ADMIN" || roleName == "MOD") {
                        hasPlayerRole = true
                        break
                    }
                }

                isPlayer = hasPlayerRole
                return@withContext Result.success(hasPlayerRole)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    fun logout() {
        accessToken = null
        userEmail = null
        isPlayer = false
    }
}
