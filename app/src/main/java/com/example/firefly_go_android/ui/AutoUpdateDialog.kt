package com.example.firefly_go_android

import AutoUpdaterManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.example.firefly_go_android.R
import com.example.autoupdater.UpdateFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
                dismissOnClickOutside = !isDownloading,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .scale(scaleAnimation)
                    .animateContentSize()
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (update != null) Icons.Rounded.SystemUpdate
                            else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (update != null) Color(0xFF2196F3) else Color(0xFF4CAF50)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (update != null) stringResource(id = R.string.update_available) else stringResource(id = R.string.no_update_available),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                                text = stringResource(id = R.string.app_up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))


                            Button(
                                onClick = { showDialog = false; onDismiss() },
                                modifier = Modifier.wrapContentWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xCC0D47A1),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.ok),
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

@Composable
fun VersionInfoSection(update: UpdateFeatures) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
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
                    text = stringResource(id = R.string.latest_version),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0D47A1)
                ) {
                    Text(
                        text = "v${update.latestversion}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun ChangelogSection(update: UpdateFeatures) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.whats_new),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = update.changelog,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 20.sp
        )
    }
}

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
                text = if (downloadComplete) stringResource(id = R.string.installation_ready) else stringResource(id = R.string.downloading),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (downloadComplete) Color(0xFF4CAF50) else Color(0xFF2196F3),
            trackColor = Color.White.copy(alpha = 0.15f),
        )
    }
}

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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text(text = stringResource(id = R.string.later), style = MaterialTheme.typography.labelLarge)
            }
        }

        Button(
            onClick = onDownloadClick,
            modifier = if (downloadComplete) Modifier.widthIn(min = 160.dp) else Modifier.weight(1f),
            enabled = !isDownloading || downloadComplete,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (downloadComplete) Color(0xFF4CAF50) else Color(0xCC0D47A1)
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                when {
                    downloadComplete -> {
                        Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.install_now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    isDownloading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
