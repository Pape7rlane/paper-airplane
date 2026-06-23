package com.example.firefly_go_android

import AutoUpdaterManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoupdater.UpdateFeatures
import com.example.firefly_go_android.ui.theme.FireflyPsAndoridTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import android.os.PowerManager
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.provider.Settings

import com.example.firefly_go_android.network.AuthManager
import com.example.firefly_go_android.ui.ScamWarningDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

data class AppVersion(
    val latestVersion: String,
    val changelog: String,
    val apkUrl: String
)

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private var onAuthStatusChanged: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBatteryExemption(this)
        requestInstallPermission(this)
        requestStoragePermission(this)

        authManager = AuthManager(this)

        val appDataPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FireflyGo").absolutePath
        val dataDir = File("$appDataPath/data")
        if (!dataDir.exists()) dataDir.mkdirs()

        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

        val currentVersionCode = if (Build.VERSION.SDK_INT >= 33) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val currentLastUpdateTime = packageInfo.lastUpdateTime

        val savedVersionCode = sharedPrefs.getLong("last_version_code", 0L)
        val savedLastUpdateTime = sharedPrefs.getLong("last_update_time", 0L)

        val isFolderEmpty = dataDir.listFiles()?.isEmpty() ?: true

        val shouldOverride = currentVersionCode > savedVersionCode || 
                           currentLastUpdateTime > savedLastUpdateTime || 
                           isFolderEmpty

        Log.i("AppUpdate", "Code: $currentVersionCode, LastUpdate: $currentLastUpdateTime")
        Log.i("AppUpdate", "SavedCode: $savedVersionCode, SavedUpdate: $savedLastUpdateTime")
        Log.i("AppUpdate", "Should Override: $shouldOverride")

        if (copyRawToFile(this, dataDir, shouldOverride)) {
            if (shouldOverride) {
                sharedPrefs.edit()
                    .putLong("last_version_code", currentVersionCode)
                    .putLong("last_update_time", currentLastUpdateTime)
                    .apply()
                Log.i("AppUpdate", "Updated SharedPreferences with new version and time")
            }
        }

        val jsonString = try {
            resources.openRawResource(R.raw.app_version_json).use { input ->
                input.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            "{}"
        }

        val jsonObject = if (jsonString.isNotEmpty()) JSONObject(jsonString) else JSONObject()
        val latestVersion = jsonObject.optString("latest_version", "1.0.0")
        val changelog = jsonObject.optString("changelog", "")
        val apkUrl = jsonObject.optString("apk_url", "")

        val appVersion = AppVersion(latestVersion, changelog, apkUrl)

        handleDeepLink(intent)

        enableEdgeToEdge()
        setContent {
            FireflyPsAndoridTheme {
                var isCheckingAuth by remember { mutableStateOf(authManager.isLoggedIn) }
                var authStateTrigger by remember { mutableIntStateOf(0) }
                var hasCheckedUpdate by remember { mutableStateOf(false) }
                var showScamWarning by remember { mutableStateOf(true) }

                onAuthStatusChanged = {
                    authStateTrigger++
                }

                LaunchedEffect(Unit) {
                    if (authManager.isLoggedIn) {
                        authManager.accessToken?.let { token ->
                            val result = authManager.fetchProfileAndVerifyRole(token)
                            if (result.isFailure) {
                                onAuthStatusChanged?.invoke()
                            }
                        }
                    }
                    isCheckingAuth = false
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        key(authStateTrigger) {
                            ServerControlScreen(appDataPath, dataDir, appVersion, authManager, Modifier.padding(innerPadding))
                            if (!showScamWarning && !hasCheckedUpdate) {
                                AutoUpdateDialog(
                                    onDismiss = { hasCheckedUpdate = true },
                                    appVersion,
                                    dataDir,
                                    true
                                )
                            }
                        }
                        if (showScamWarning) {
                            ScamWarningDialog(onDismiss = { showScamWarning = false })
                        }
                    }
                }
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "firefly-launcher" && data.host == "auth" && data.path == "/discord") {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                authManager.accessToken = token
                lifecycleScope.launch {
                    val result = authManager.fetchProfileAndVerifyRole(token)
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "Logged in via Discord successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to get Discord profile: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                    onAuthStatusChanged?.invoke()
                }
            }
        }
    }

}
@SuppressLint("BatteryLife")

fun requestBatteryExemption(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent().apply {
            action = ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    }
}

fun requestInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please allow installing unknown apps to update", Toast.LENGTH_LONG).show()
        }
    }
}

fun requestStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please allow All Files Access to load game data", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("StoragePermission", "Failed to open settings", ex)
                }
            }
        }
    }
}

fun copyRawToFile(context: Context, targetDir: File, override: Boolean = false): Boolean {
    val files = listOf(
        "data-in-game.json" to "data-in-game.json",
        "freesr-data.json" to "freesr-data.json",
        "version.json" to "version.json"
    )

    return try {
        if (!targetDir.exists()) targetDir.mkdirs()

        for ((assetFile, outName) in files) {
            val outFile = File(targetDir, outName)

            if (outFile.exists() && !override) {
                Log.i("CopyRaw", "Skipping $outName (already exists and no override)")
                continue
            }

            Log.i("CopyRaw", "Copying $assetFile to ${outFile.absolutePath} (Override: $override)")
            context.assets.open(assetFile).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        }

        true
    } catch (e: Exception) {
        Log.e("CopyRaw", "Error copying asset file: ${e.message}", e)
        false
    }
}

fun removeFile(targetDir: File, fileName: String): Boolean {
    val file = File(targetDir, fileName)
    return if (file.exists()) {
        try {
            if (file.delete()) {
                Log.i("FileRemove", "Removed $fileName from ${file.absolutePath}")
                true
            } else {
                Log.e("FileRemove", "Failed to remove $fileName from ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Log.e("FileRemove", "Error removing $fileName: ${e.message}")
            false
        }
    } else {
        Log.i("FileRemove", "$fileName does not exist in ${targetDir.absolutePath}")
        false
    }
}


@Composable
fun Modifier.bounceClick(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
