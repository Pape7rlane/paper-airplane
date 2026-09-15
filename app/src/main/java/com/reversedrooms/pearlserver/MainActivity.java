package com.reversedrooms.pearlserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private TextView status;
    private TextView logView;
    private View logScroll;
    private ScrollView logContentScroll;
    private TextView startButton;
    private TextView updateInfo;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private Process dispatchProcess;
    private Process gameProcess;
    private File serverDir;
    private boolean serverRunning = false;

    private static final String RELEASE_API =
            "https://api.github.com/repos/Pape7rlane/paper-airplane/releases/latest";

    private static final String PREFS_NAME = "pearl_sr_launcher";
    private static final String PREF_BACKGROUND_ASKED = "background_permission_asked";

    private String latestVersion = null;
    private String latestApkUrl = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        logView = findViewById(R.id.logView);
        logScroll = findViewById(R.id.logScroll);
        logContentScroll = findViewById(R.id.logContentScroll);
        startButton = findViewById(R.id.startButton);
        updateInfo = findViewById(R.id.updateInfo);

        try {
            prepareServerFiles();
            appendLog("服务端文件已准备。");
            appendLog("工作目录: " + serverDir.getAbsolutePath());
            appendLog("Dispatch: " + nativePath("libdispatch.so"));
            appendLog("GameServer: " + nativePath("libgameserver.so"));
        } catch (Exception e) {
            appendLog("初始化失败: " + e);
            statusText("初始化失败");
        }

        startButton.setOnClickListener(v -> {
            if (serverRunning || isAlive(dispatchProcess) || isAlive(gameProcess)) {
                stopServers();
            } else {
                startServers();
            }
        });

        // 只有用户主动点击“更新”时才检查 GitHub 最新版本。
        View updateButton = findViewById(R.id.updateButton);
        updateButton.setOnClickListener(v -> checkForUpdates(true));

        // 版本信息只用于显示，不再作为更新入口。
        updateInfo.setClickable(false);
        updateInfo.setFocusable(false);
        updateInfo.setText("当前版本 " + BuildConfig.VERSION_NAME + " · 点击“更新”检查");

        // 已删除“重置”功能。

        View logButton = findViewById(R.id.logButton);
        logButton.setOnClickListener(v -> {
            if (logScroll.getVisibility() == View.VISIBLE) {
                logScroll.setVisibility(View.GONE);
            } else {
                logScroll.setVisibility(View.VISIBLE);
                logContentScroll.post(() -> logContentScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });

        View closeLogButton = findViewById(R.id.closeLogButton);
        closeLogButton.setOnClickListener(v -> logScroll.setVisibility(View.GONE));

        View srToolsButton = findViewById(R.id.srToolsButton);
        srToolsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SRToolsActivity.class);
            startActivity(intent);
        });

        // 每次打开应用显示安全警告；后台运行询问只在首次启动显示。
        showSecurityWarning(() -> showBackgroundPermissionIfNeeded());
    }

    /** 每次启动应用显示安全警告。关闭后，如果是首次启动，再询问后台运行。 */
    private void showSecurityWarning(Runnable afterDismiss) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_security_warning, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.setOnShowListener(d -> {
            TextView button = dialogView.findViewById(R.id.securityWarningButton);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                if (afterDismiss != null) afterDismiss.run();
            });

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.dimAmount = 0.72f;
                dialog.getWindow().setAttributes(params);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
                dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 首次启动询问是否允许应用后台运行。 */
    private void showBackgroundPermissionIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_BACKGROUND_ASKED, false)) return;

        new AlertDialog.Builder(this)
                .setTitle("允许后台运行")
                .setMessage("为了让服务端在切换到后台后继续运行，是否允许本应用后台运行？\n\n选择“允许”后，将尝试打开系统的电池优化设置。")
                .setNegativeButton("暂不允许", (dialog, which) -> {
                    prefs.edit().putBoolean(PREF_BACKGROUND_ASKED, true).apply();
                    appendLog("用户未允许后台运行。");
                })
                .setPositiveButton("允许", (dialog, which) -> {
                    prefs.edit().putBoolean(PREF_BACKGROUND_ASKED, true).apply();
                    requestBackgroundRunningPermission();
                })
                .setCancelable(false)
                .show();
    }

    /** 请求系统忽略电池优化，让服务器更不容易被后台限制。 */
    private void requestBackgroundRunningPermission() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    appendLog("系统已允许后台运行。");
                    showToast("后台运行权限已开启");
                    return;
                }

                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
                appendLog("已打开系统后台运行权限设置。");
                return;
            }
        } catch (Exception e) {
            appendLog("打开后台运行设置失败: " + e.getMessage());
        }

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            appendLog("无法打开应用设置: " + e.getMessage());
        }
    }

    private String nativePath(String name) {
        return getApplicationInfo().nativeLibraryDir + File.separator + name;
    }

    private void prepareServerFiles() throws IOException {
        serverDir = new File(getFilesDir(), "4.5.54");
        if (!serverDir.exists() && !serverDir.mkdirs()) {
            throw new IOException("无法创建服务端目录");
        }
        copyAssetTree("server", serverDir);
        setExecutable(new File(nativePath("libdispatch.so")));
        setExecutable(new File(nativePath("libgameserver.so")));
    }

    private void copyAssetTree(String assetPath, File out) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assetPath, out);
            return;
        }
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("无法创建: " + out);
        }
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, new File(out, child));
        }
    }

    private void copyAssetFile(String assetPath, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建: " + parent);
        }
        try (InputStream in = getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[1024 * 64];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private void setExecutable(File f) {
        try { f.setExecutable(true, false); } catch (Exception ignored) {}
    }

    private synchronized void startServers() {
        if (isAlive(dispatchProcess) || isAlive(gameProcess)) {
            appendLog("服务端已经在运行。");
            return;
        }

        final String dispatch = nativePath("libdispatch.so");
        final String gameserver = nativePath("libgameserver.so");

        io.execute(() -> {
            try {
                serverRunning = true;
                statusText("正在启动 Dispatch...");
                startButtonRunning(true);
                startButtonText("■  停止服务器");

                dispatchProcess = new ProcessBuilder(dispatch)
                        .directory(serverDir)
                        .redirectErrorStream(true)
                        .start();
                readOutput("Dispatch", dispatchProcess.getInputStream());

                Thread.sleep(800);

                if (dispatchProcess != null && dispatchProcess.isAlive()) {
                    statusText("Dispatch 已启动，正在启动 GameServer...");
                    gameProcess = new ProcessBuilder(gameserver)
                            .directory(serverDir)
                            .redirectErrorStream(true)
                            .start();
                    readOutput("GameServer", gameProcess.getInputStream());
                    statusText("服务器运行中");
                    startButtonRunning(true);
                    startButtonText("■  停止服务器");
                } else {
                    serverRunning = false;
                    startButtonRunning(false);
                    startButtonText("▶  启动服务器");
                    statusText("服务器启动失败");
                    appendLog("Dispatch 已退出，未启动 GameServer。");
                }
            } catch (Exception e) {
                serverRunning = false;
                startButtonRunning(false);
                startButtonText("▶  启动服务器");
                statusText("启动失败");
                appendLog("启动异常: " + e);
            }
        });
    }

    private void readOutput(String tag, InputStream input) {
        io.execute(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
                String line;
                while ((line = br.readLine()) != null) appendLog("[" + tag + "] " + line);
            } catch (IOException e) {
                appendLog("[" + tag + "] 日志读取结束: " + e.getMessage());
            }
        });
    }

    private synchronized void stopServers() {
        if (gameProcess != null) {
            appendLog("停止 GameServer...");
            gameProcess.destroy();
            gameProcess = null;
        }
        if (dispatchProcess != null) {
            appendLog("停止 Dispatch...");
            dispatchProcess.destroy();
            dispatchProcess = null;
        }
        serverRunning = false;
        startButtonRunning(false);
        startButtonText("▶  启动服务器");
        statusText("服务器已停止");
    }

    private boolean isAlive(Process p) {
        return p != null && p.isAlive();
    }

    /** 只有点击“更新”后才执行这里。 */
    private void checkForUpdates(boolean manual) {
        updateInfo.setText("正在检查最新版本...");
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "PearlSR-Android-Updater");

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("GitHub API HTTP " + code);
                }

                StringBuilder json = new StringBuilder();
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                }

                String tag = extractJsonString(json.toString(), "tag_name");
                String apkUrl = extractApkUrl(json.toString());
                if (tag == null || apkUrl == null) {
                    throw new IOException("最新 Release 中没有找到 APK");
                }

                latestVersion = normalizeVersion(tag);
                latestApkUrl = apkUrl;
                final boolean hasUpdate = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0;

                main.post(() -> {
                    if (hasUpdate) {
                        updateInfo.setText("发现新版本 " + latestVersion + " · 正在下载");
                        appendLog("发现新版本: " + latestVersion);
                        // 不显示 APK 下载链接，直接下载。
                        io.execute(this::downloadApk);
                    } else {
                        updateInfo.setText("当前已是最新版本 " + BuildConfig.VERSION_NAME);
                        appendLog("当前已是最新版本: " + BuildConfig.VERSION_NAME);
                        new AlertDialog.Builder(this)
                                .setTitle("已是最新版本")
                                .setMessage("当前版本：" + BuildConfig.VERSION_NAME + "\n\n暂时没有可用的新版本。")
                                .setPositiveButton("确定", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                main.post(() -> updateInfo.setText("检查更新失败 · 请重试"));
                appendLog("检查更新失败: " + e.getMessage());
                if (manual) showToast("检查更新失败: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : null;
    }

    private String extractApkUrl(String json) {
        Pattern pattern = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : null;
    }

    private String normalizeVersion(String value) {
        String v = value == null ? "" : value.trim();
        while (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        int space = v.indexOf(' ');
        if (space > 0) v = v.substring(0, space);
        return v;
    }

    private int compareVersions(String a, String b) {
        String[] pa = normalizeVersion(a).split("\\.");
        String[] pb = normalizeVersion(b).split("\\.");
        int length = Math.max(pa.length, pb.length);
        for (int i = 0; i < length; i++) {
            int va = i < pa.length ? numericPart(pa[i]) : 0;
            int vb = i < pb.length ? numericPart(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private int numericPart(String value) {
        Matcher m = Pattern.compile("\\d+").matcher(value == null ? "" : value);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /** 下载完成后直接调起 Android 安装器。 */
    private void downloadApk() {
        if (latestApkUrl == null || latestVersion == null) {
            showToast("还没有获取到最新版本信息");
            return;
        }

        File external = getExternalFilesDir(null);
        if (external == null) {
            showToast("无法访问应用存储目录");
            return;
        }

        File updateDir = new File(external, "update");
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            showToast("无法创建更新目录");
            return;
        }

        File apk = new File(updateDir, "pearl-sr-" + latestVersion + "-update.apk");
        HttpURLConnection connection = null;

        try {
            statusText("正在下载 " + latestVersion + "...");
            appendLog("开始下载 " + latestVersion);

            URL url = new URL(latestApkUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "PearlSR-Android-Updater");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);

            int total = connection.getContentLength();
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(apk))) {
                byte[] buffer = new byte[1024 * 64];
                long downloaded = 0;
                int length;
                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                    downloaded += length;
                    if (total > 0) {
                        int progress = (int) (downloaded * 100 / total);
                        main.post(() -> statusText("正在下载 " + latestVersion + " · " + progress + "%"));
                    }
                }
            }

            appendLog("APK 下载完成: " + apk.length() + " bytes");
            statusText("更新下载完成");
            main.post(() -> installApk(apk));

        } catch (Exception e) {
            appendLog("更新失败: " + e);
            statusText("更新失败");
            showToast("更新失败: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            appendLog("无法打开 APK 安装程序: " + e);
            showToast("无法安装 APK，请检查系统的“允许安装未知应用”权限。");
        }
    }

    private void showToast(String text) {
        main.post(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    private void statusText(String text) {
        main.post(() -> status.setText(text));
    }

    private void startButtonText(String text) {
        main.post(() -> startButton.setText(text));
    }

    private void startButtonRunning(boolean running) {
        main.post(() -> startButton.setBackgroundResource(
                running ? R.drawable.bg_start_button_running : R.drawable.bg_start_button));
    }

    private void appendLog(String text) {
        main.post(() -> {
            logView.append(text + "\n");
            if (logScroll.getVisibility() == View.VISIBLE) {
                logContentScroll.post(() -> logContentScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopServers();
        io.shutdownNow();
        super.onDestroy();
    }
}
