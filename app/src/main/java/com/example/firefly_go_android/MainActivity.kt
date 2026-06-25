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
<<<<<<< HEAD
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
=======
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
>>>>>>> 2459650 (feat: v4)
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

<<<<<<< HEAD
=======
import com.example.firefly_go_android.network.AuthManager
import com.example.firefly_go_android.ui.ScamWarningDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

>>>>>>> 2459650 (feat: v4)
data class AppVersion(
    val latestVersion: String,
    val changelog: String,
    val apkUrl: String
)

class MainActivity : ComponentActivity() {
<<<<<<< HEAD
=======
    private lateinit var authManager: AuthManager
    private var onAuthStatusChanged: (() -> Unit)? = null

>>>>>>> 2459650 (feat: v4)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBatteryExemption(this)
        requestInstallPermission(this)
        requestStoragePermission(this)

<<<<<<< HEAD
=======
        authManager = AuthManager(this)

>>>>>>> 2459650 (feat: v4)
        val appDataPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FireflyGo").absolutePath
        val dataDir = File("$appDataPath/data")
        if (!dataDir.exists()) dataDir.mkdirs()

        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

<<<<<<< HEAD
        // Lấy thông tin Package
=======
>>>>>>> 2459650 (feat: v4)
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

<<<<<<< HEAD
        enableEdgeToEdge()
        setContent {
            FireflyPsAndoridTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ServerControlScreen(appDataPath, dataDir, appVersion, Modifier.padding(innerPadding))
                        AutoUpdateDialog(onDismiss = {}, appVersion, dataDir, true)
=======
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
>>>>>>> 2459650 (feat: v4)
                    }
                }
            }
        }

    }

<<<<<<< HEAD
=======
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

>>>>>>> 2459650 (feat: v4)
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
<<<<<<< HEAD
=======

>>>>>>> 2459650 (feat: v4)
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


<<<<<<< HEAD
@SuppressLint("ImplicitSamInstance")
@Composable
fun ServerControlScreen(appDataPath: String, dataDir: File, appVersion: AppVersion, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRunning = GolangServerService.isRunning

    var showResetDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Firefly GO for Android",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp),
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                )
            )

            // Server status with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.PlayCircleFilled else Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = if (isRunning) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Server is running" else "Server is stopped",
                    fontSize = 30.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(1f, 1f),
                            blurRadius = 2f
                        )
                    )
                )
            }


            Spacer(modifier = Modifier.height(200.dp))
            // Toggle button
            Button(
                onClick = {
                    try {
                        if (!isRunning) {
                            val intent = Intent(context, GolangServerService::class.java)
                            intent.putExtra("appDataPath", appDataPath)
                            ContextCompat.startForegroundService(context, intent)
                        } else {
                            context.stopService(Intent(context, GolangServerService::class.java))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFB71C1C) else Color(0xFF2196F3),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Stop Server" else "Start Server",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Widget icons row
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Check Update widget
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { showUpdateDialog = true }
                        .background(
                            Color.White.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Check Update",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Update",
                        fontSize = 12.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Reset Data widget
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { showResetDialog = true }
                        .background(
                            Color.White.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Data",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reset",
                        fontSize = 12.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Logcat (Lynx) widget
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable {
                            showLogs = true // mở popup log
                        }
                        .background(
                            Color.White.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Open Logcat",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Logs",
                        fontSize = 12.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(75.dp))
        }
    }

    if (showLogs) {
        LogPopup(onDismiss = { showLogs = false })
    }
    // Reset Data Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(text = "Reset Data")
            },
            text = {
                Text(text = "Do you want reset all data? This action can not rollback.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        try {
                            copyRawToFile(context, dataDir, true)
                            Toast.makeText(context, "Data has been reset successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Yes", color = Color(0xFFFF0606))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text("No")
                }
            }
        )
    }

    // Auto Update Dialog
    if (showUpdateDialog) {
        AutoUpdateDialog(
            onDismiss = { showUpdateDialog = false },
            appVersion,
            dataDir
        )
    }
}

fun parseGoLogLine(line: String): String? {
    val regex = Regex(""".*GoLog\s*:?\s*(.*)""")
    val match = regex.find(line)
    val content = match?.groupValues?.getOrNull(1)?.trim()

    return if (content.isNullOrBlank()) null else content
}


fun parseAnsi(text: String, defaultColor: Color): AnnotatedString {
    val regex = Regex("\u001B\\[(\\d+)(;\\d+)*m")
    val builder = buildAnnotatedString {
        var lastIndex = 0
        var currentColor = defaultColor

        for (match in regex.findAll(text)) {
            val start = match.range.first

            // 1. Thêm phần text TRƯỚC mã ANSI với màu HIỆN TẠI
            val before = text.substring(lastIndex, start)
            if (before.isNotEmpty()) {
                withStyle(SpanStyle(color = currentColor)) {
                    append(before)
                }
            }

            // 2. Lấy mã code (ví dụ 31, 36, hoặc 0)
            val code = try {
                match.groupValues[1].toInt()
            } catch (e: NumberFormatException) {
                0
            }

            currentColor = when (code) {
                0 -> defaultColor
                30 -> Color.Black
                31 -> Color.Red
                32 -> Color(0xFF00C853) // Green
                33 -> Color(0xFFFFD600) // Yellow
                34 -> Color(0xFF2962FF) // Blue
                35 -> Color(0xFFD500F9) // Magenta
                36 -> Color(0xFF00B8D4) // Cyan
                37 -> Color.White
                else -> currentColor
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            val remain = text.substring(lastIndex)
            if (remain.isNotEmpty()) {
                withStyle(SpanStyle(color = currentColor)) {
                    append(remain)
                }
            }
        }
    }

    return builder
}

@Composable
fun LogPopup(
    onDismiss: () -> Unit
) {
    var logs by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -s GoLog")
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val clean = parseGoLogLine(line!!)
                    if (!clean.isNullOrBlank()) {
                        logs = (logs + clean).takeLast(200)
                    }
                }
            } catch (e: Exception) {
                logs = logs + "Error reading logcat: ${e.message}"
            }
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val defaultTextColor = LocalContentColor.current

    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GoLog Output",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(logs.size) { index ->
                        Text(
                            text = parseAnsi(logs[index], defaultTextColor),
                            fontSize = 12.sp,

                            // 2. DÙNG FONT MONOSPACE
                            fontFamily = FontFamily.Monospace,

                            // 3. (Tuỳ chọn) Giảm chiều cao dòng để logo liền mạch
                            lineHeight = 14.sp,

                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}



@Composable
fun AutoUpdateDialog(
    onDismiss: () -> Unit,
    appVersion: AppVersion,
    dataDir: File,
    isFirstOpen: Boolean = false
) {
    val context = LocalContext.current
    val autoUpdaterManager = AutoUpdaterManager(context)
    var update by remember { mutableStateOf<UpdateFeatures?>(null) }
    var progress by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadComplete by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val progressAnimation by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val scaleAnimation by animateFloatAsState(
        targetValue = if (showDialog) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Check for update
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            autoUpdaterManager.checkForUpdate(
                JSONfileURL = "https://git.kain.io.vn/Firefly-Shelter/FireflyGo_Andoid/raw/branch/master/app/src/main/res/raw/app_version_json.json"
            )
        }

        val hasUpdate = result != null && appVersion.latestVersion != result.latestversion

        update = if (hasUpdate) result else null

        showDialog = if (isFirstOpen) {
            hasUpdate
        } else {
            result != null
        }
    }


    // Download progress
    LaunchedEffect(progress) {
        if (progress >= 100 && isDownloading) {
            downloadComplete = true
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                if (!isDownloading) showDialog = false
                onDismiss()
            },
            properties = DialogProperties(
                dismissOnBackPress = !isDownloading,
                dismissOnClickOutside = !isDownloading
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .scale(scaleAnimation)
                    .animateContentSize(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (update != null) Icons.Rounded.SystemUpdate
                            else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = if (update != null) "Update Available" else "No Update Available",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (update != null) {
                        VersionInfoSection(update!!)
                        ChangelogSection(update!!)
                        DownloadProgressSection(
                            isDownloading = isDownloading,
                            downloadComplete = downloadComplete,
                            progress = progressAnimation
                        )
                        ActionButtons(
                            isDownloading = isDownloading,
                            downloadComplete = downloadComplete,
                            onDownloadClick = {
                                isDownloading = true
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        autoUpdaterManager.downloadapk(
                                            context,
                                            update!!.apk_url,
                                            "FireflyGO_${update!!.latestversion}.apk"
                                        ) { prog -> progress = prog }
                                    }
                                }
                            },
                            onDismiss = { showDialog = false; onDismiss() }
                        )
                    } else {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Your app is up to date",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))


                            Button(
                                onClick = { showDialog = false; onDismiss() },
                                modifier = Modifier.wrapContentWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "OK",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Version info card
@Composable
fun VersionInfoSection(update: UpdateFeatures) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Latest Version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "v${update.latestversion}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

// Changelog section
@Composable
fun ChangelogSection(update: UpdateFeatures) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "What's New",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = update.changelog,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

// Progress section
@Composable
fun DownloadProgressSection(
    isDownloading: Boolean,
    downloadComplete: Boolean,
    progress: Float
) {
    if (!isDownloading && !downloadComplete) return
    Spacer(modifier = Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (downloadComplete) "Installation Ready" else "Downloading...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(progress*100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
        color = if (downloadComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// Action buttons
@Composable
fun ActionButtons(
    isDownloading: Boolean,
    downloadComplete: Boolean,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (!isDownloading && !downloadComplete) Arrangement.spacedBy(12.dp) else Arrangement.Center
    ) {
        if (!downloadComplete) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                enabled = !isDownloading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Later", style = MaterialTheme.typography.labelLarge)
            }
        }

        Button(
            onClick = onDownloadClick,
            modifier = if (downloadComplete) Modifier.widthIn(min = 160.dp) else Modifier.weight(1f),
            enabled = !isDownloading || downloadComplete,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (downloadComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                when {
                    downloadComplete -> {
                        Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Install Now", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }
                    isDownloading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
=======
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
>>>>>>> 2459650 (feat: v4)
}
