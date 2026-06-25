package com.example.firefly_go_android

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.example.firefly_go_android.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class LogEntry(val id: Long, val text: String)

@Composable
fun LogPopup(
    onDismiss: () -> Unit
) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    var nextLogId by remember { mutableLongStateOf(0L) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            var process: Process? = null
            var reader: BufferedReader? = null
            try {
                process = Runtime.getRuntime().exec("logcat -T 300 -s GoLog")
                reader = BufferedReader(InputStreamReader(process.inputStream))

                // Safely destroy the logcat process and close the stream when the coroutine is cancelled
                coroutineContext[Job]?.invokeOnCompletion {
                    try {
                        process?.destroy()
                        reader?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val clean = parseGoLogLine(line!!)
                    if (!clean.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            logs.add(LogEntry(nextLogId++, clean))
                            if (logs.size > 500) {
                                logs.removeAt(0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs.add(LogEntry(nextLogId++, "Error reading logcat: ${e.message}"))
                }
            } finally {
                try {
                    process?.destroy()
                    reader?.close()
                } catch (e: Exception) {
                    // ignore
                }
                Log.i("LogPopup", "Logcat process destroyed and stream closed successfully")
            }
        }
    }

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    val defaultTextColor = Color.White.copy(alpha = 0.9f)

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.golog_output),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Auto-Scroll Toggle Button
                    val activeColor = Color(0xFF4CAF50)
                    val inactiveColor = Color.White.copy(alpha = 0.4f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { autoScroll = !autoScroll }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (autoScroll) activeColor else inactiveColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.auto_scroll),
                            fontSize = 11.sp,
                            color = if (autoScroll) Color.White else Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(
                        items = logs,
                        key = { log -> log.id }
                    ) { log ->
                        Text(
                            text = parseAnsi(log.text, defaultTextColor),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            logs.clear()
                            try {
                                Runtime.getRuntime().exec("logcat -c")
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    ) {
                        Text(stringResource(id = R.string.clear), color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { onDismiss() }
                    ) {
                        Text(stringResource(id = R.string.close), color = Color.White)
                    }
                }
            }
        }
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

            val before = text.substring(lastIndex, start)
            if (before.isNotEmpty()) {
                withStyle(SpanStyle(color = currentColor)) {
                    append(before)
                }
            }

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
