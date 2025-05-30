package com.example.fireflypsandorid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fireflypsandorid.ui.theme.FireflyPsAndoridTheme
import java.io.*

class MainActivity : ComponentActivity() {
    private val TAG = "AppInit"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appDataPath = filesDir.absolutePath
        val dataDir = File("$appDataPath/data")
        dataDir.mkdirs()

        checkAndCreateFile(dataDir, "data-in-game.json", R.raw.data_in_game_json)
        checkAndCreateFile(dataDir, "freesr-data.json", R.raw.freesr_data_json)
        checkAndCreateFile(dataDir, "version.json", R.raw.version_json)

        enableEdgeToEdge()
        setContent {
            FireflyPsAndoridTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ServerControlScreen(appDataPath, Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkAndCreateFile(targetDir: File, fileName: String, resId: Int) {
        val outFile = File(targetDir, fileName)
        if (!outFile.exists()) {
            try {
                resources.openRawResource(resId).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "✅ Copied $fileName to ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy $fileName: ${e.message}")
            }
        } else {
            Log.i(TAG, "ℹ️ $fileName already exists at ${outFile.absolutePath}")
        }
    }
}


@Composable
fun ServerControlScreen(appDataPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(false) }

    val serverImage = if (isServerRunning)
        painterResource(id = R.drawable.server_running)
    else
        painterResource(id = R.drawable.server_stopped)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Firefly Ps for Android",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 24.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.9f) // Lime Green
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Server status image
        Image(
            painter = serverImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle button
        Button(
            onClick = {
                try {
                    isServerRunning = !isServerRunning
                    if (isServerRunning) {
                        val intent = Intent(context, GolangServerService::class.java)
                        intent.putExtra("appDataPath", appDataPath)
                        context.startService(intent)
                    } else {
                        context.stopService(Intent(context, GolangServerService::class.java))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServerRunning) Color(0xFFB71C1C) else Color(0xFFFF5722),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
        ) {
            Text(
                text = if (isServerRunning) "Stop Server" else "Start Server",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Server status text
        Text(
            text = if (isServerRunning) "Server is running" else "Server is stopped",
            fontSize = 24.sp,
            color = if (isServerRunning) Color(0xFF4CAF50) else Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
