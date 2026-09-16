package com.reversedrooms.pearlserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView status;
    private TextView logView;
    private View logScroll;
    private ScrollView logContentScroll;
    private TextView startButton;
    private TextView updateInfo;

    private final Handler main =
            new Handler(Looper.getMainLooper());

    private final ExecutorService io =
            Executors.newFixedThreadPool(3);

    private Process dispatchProcess;
    private Process gameProcess;

    private File serverDir;

    private boolean serverRunning = false;

    /*
     * ============================================================
     * GitHub 更新配置
     * ============================================================
     */

    private static final String GITHUB_OWNER =
            "Pape7rlane";

    private static final String GITHUB_REPO =
            "paper-airplane";

    private static final String RELEASE_API =
            "https://api.github.com/repos/"
                    + GITHUB_OWNER
                    + "/"
                    + GITHUB_REPO
                    + "/releases/latest";

    /*
     * Android SharedPreferences
     */

    private static final String PREFS_NAME =
            "pearl_sr_launcher";

    private static final String PREF_BACKGROUND_ASKED =
            "background_permission_asked";

    /*
     * 最新版本信息
     */

    private String latestVersion = null;

    /*
     * GitHub Release Asset API URL
     *
     * 例如：
     * https://api.github.com/repos/Pape7rlane/
     * paper-airplane/releases/assets/123456789
     */
    private String latestApkApiUrl = null;

    /*
     * GitHub 浏览器下载地址
     *
     * 例如：
     * https://github.com/Pape7rlane/paper-airplane/
     * releases/download/...
     */
    private String latestApkBrowserUrl = null;


    // ============================================================
    // Activity
    // ============================================================

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

        /*
         * ========================================================
         * 准备服务器文件
         * ========================================================
         */

        try {

            prepareServerFiles();

            appendLog("服务端文件已准备。");

            appendLog(
                    "工作目录: "
                            + serverDir.getAbsolutePath()
            );

            appendLog(
                    "Dispatch: "
                            + nativePath("libdispatch.so")
            );

            appendLog(
                    "GameServer: "
                            + nativePath("libgameserver.so")
            );

        } catch (Exception e) {

            appendLog("初始化失败: " + e);

            statusText("初始化失败");
        }


        /*
         * ========================================================
         * 启动 / 停止服务器
         * ========================================================
         */

        startButton.setOnClickListener(v -> {

            if (
                    serverRunning
                            || isAlive(dispatchProcess)
                            || isAlive(gameProcess)
            ) {

                stopServers();

            } else {

                startServers();
            }
        });


        /*
         * ========================================================
         * 更新按钮
         * ========================================================
         */

        View updateButton =
                findViewById(R.id.updateButton);

        updateButton.setOnClickListener(
                v -> checkForUpdates(true)
        );


        /*
         * ========================================================
         * 当前版本
         * ========================================================
         */

        updateInfo.setClickable(false);
        updateInfo.setFocusable(false);

        updateInfo.setText(
                "当前版本 "
                        + BuildConfig.VERSION_NAME
                        + " · 点击“更新”检查"
        );


        /*
         * ========================================================
         * 日志按钮
         * ========================================================
         */

        View logButton =
                findViewById(R.id.logButton);

        logButton.setOnClickListener(v -> {

            if (
                    logScroll.getVisibility()
                            == View.VISIBLE
            ) {

                logScroll.setVisibility(View.GONE);

            } else {

                logScroll.setVisibility(View.VISIBLE);

                logContentScroll.post(
                        () -> logContentScroll.fullScroll(
                                ScrollView.FOCUS_DOWN
                        )
                );
            }
        });


        /*
         * ========================================================
         * 关闭日志
         * ========================================================
         */

        View closeLogButton =
                findViewById(R.id.closeLogButton);

        closeLogButton.setOnClickListener(
                v -> logScroll.setVisibility(View.GONE)
        );


        /*
         * ========================================================
         * SRTools
         * ========================================================
         */

        View srToolsButton =
                findViewById(R.id.srToolsButton);

        srToolsButton.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            SRToolsActivity.class
                    );

            startActivity(intent);
        });


        /*
         * ========================================================
         * 安全警告
         * ========================================================
         */

        showSecurityWarning(
                this::showBackgroundPermissionIfNeeded
        );
    }


    // ============================================================
    // 安全警告
    // ============================================================

    private void showSecurityWarning(
            Runnable afterDismiss
    ) {

        View dialogView =
                getLayoutInflater().inflate(
                        R.layout.dialog_security_warning,
                        null
                );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(dialogView)
                        .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        dialog.setOnShowListener(d -> {

            TextView button =
                    dialogView.findViewById(
                            R.id.securityWarningButton
                    );

            button.setOnClickListener(v -> {

                dialog.dismiss();

                if (afterDismiss != null) {
                    afterDismiss.run();
                }
            });

            if (dialog.getWindow() != null) {

                dialog.getWindow()
                        .setBackgroundDrawableResource(
                                android.R.color.transparent
                        );

                android.view.WindowManager.LayoutParams params =
                        dialog.getWindow().getAttributes();

                params.dimAmount = 0.72f;

                dialog.getWindow()
                        .setAttributes(params);

                dialog.getWindow()
                        .addFlags(
                                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        );

                int width =
                        (int) (
                                getResources()
                                        .getDisplayMetrics()
                                        .widthPixels
                                        * 0.88f
                        );

                dialog.getWindow().setLayout(
                        width,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
        });

        dialog.show();

        if (dialog.getWindow() != null) {

            int width =
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels
                                    * 0.88f
                    );

            dialog.getWindow().setLayout(
                    width,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }


    // ============================================================
    // 后台运行权限
    // ============================================================

    private void showBackgroundPermissionIfNeeded() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        if (
                prefs.getBoolean(
                        PREF_BACKGROUND_ASKED,
                        false
                )
        ) {
            return;
        }

        new AlertDialog.Builder(this)

                .setTitle("允许后台运行")

                .setMessage(
                        "为了让服务端在切换到后台后继续运行，"
                                + "是否允许本应用后台运行？\n\n"
                                + "选择“允许”后，将尝试打开系统的"
                                + "应用电池设置中的“后台活动/电池使用量”页面。"
                )

                .setNegativeButton(
                        "暂不允许",
                        (dialog, which) -> {

                            prefs.edit()
                                    .putBoolean(
                                            PREF_BACKGROUND_ASKED,
                                            true
                                    )
                                    .apply();

                            appendLog(
                                    "用户未允许后台运行。"
                            );
                        }
                )

                .setPositiveButton(
                        "允许",
                        (dialog, which) -> {

                            requestBackgroundRunningPermission();
                        }
                )

                .setCancelable(false)

                .show();
    }


    // ============================================================
    // 请求后台运行
    // ============================================================

    private void requestBackgroundRunningPermission() {

        try {

            PowerManager powerManager =
                    (PowerManager)
                            getSystemService(
                                    Context.POWER_SERVICE
                            );

            if (
                    powerManager != null
                            && Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.M
            ) {

                String packageName =
                        getPackageName();

                if (
                        powerManager
                                .isIgnoringBatteryOptimizations(
                                        packageName
                                )
                ) {

                    appendLog(
                            "系统已允许后台运行。"
                    );

                    showToast(
                            "后台运行权限已开启"
                    );

                    return;
                }

                Uri packageUri = Uri.parse("package:" + packageName);
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
                try {
                    startActivity(intent);
                    prefsMarkBackgroundAsked();
                    appendLog("已打开应用电池设置，请将电池使用量设为“不受限制”。");
                    return;
                } catch (Exception ignored) {
                    // Some vendor ROMs do not expose the app detail battery page.
                }

                Intent batteryIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(batteryIntent);
                prefsMarkBackgroundAsked();
                appendLog("已打开系统电池优化列表，请允许本应用高耗电后台运行。");
                return;
            }

        } catch (Exception e) {

            appendLog(
                    "打开后台运行设置失败: "
                            + e.getMessage()
            );
        }

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    );

            intent.setData(
                    Uri.parse(
                            "package:" + getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception e) {

            appendLog(
                    "无法打开应用设置: "
                            + e.getMessage()
            );
        }
    }


    private void prefsMarkBackgroundAsked() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_BACKGROUND_ASKED, true).apply();
    }


    // ============================================================
    // Native 文件
    // ============================================================

    private String nativePath(String name) {

        return getApplicationInfo()
                .nativeLibraryDir
                + File.separator
                + name;
    }


    // ============================================================
    // 准备服务器
    // ============================================================

    private void prepareServerFiles()
            throws IOException {

        serverDir =
                new File(
                        getFilesDir(),
                        "4.5.54"
                );

        if (
                !serverDir.exists()
                        && !serverDir.mkdirs()
        ) {

            throw new IOException(
                    "无法创建服务端目录"
            );
        }

        copyAssetTree(
                "server",
                serverDir
        );

        setExecutable(
                new File(
                        nativePath(
                                "libdispatch.so"
                        )
                )
        );

        setExecutable(
                new File(
                        nativePath(
                                "libgameserver.so"
                        )
                )
        );
    }


    private void copyAssetTree(
            String assetPath,
            File out
    ) throws IOException {

        String[] children =
                getAssets().list(assetPath);

        if (
                children == null
                        || children.length == 0
        ) {

            copyAssetFile(
                    assetPath,
                    out
            );

            return;
        }

        if (
                !out.exists()
                        && !out.mkdirs()
        ) {

            throw new IOException(
                    "无法创建: " + out
            );
        }

        for (String child : children) {

            copyAssetTree(
                    assetPath + "/" + child,
                    new File(out, child)
            );
        }
    }


    private void copyAssetFile(
            String assetPath,
            File out
    ) throws IOException {

        File parent =
                out.getParentFile();

        if (
                parent != null
                        && !parent.exists()
                        && !parent.mkdirs()
        ) {

            throw new IOException(
                    "无法创建: " + parent
            );
        }

        try (
                InputStream in =
                        getAssets().open(assetPath);

                OutputStream os =
                        new FileOutputStream(out)
        ) {

            byte[] buf =
                    new byte[1024 * 64];

            int n;

            while (
                    (n = in.read(buf)) != -1
            ) {

                os.write(
                        buf,
                        0,
                        n
                );
            }
        }
    }


    private void setExecutable(File f) {

        try {

            f.setExecutable(
                    true,
                    false
            );

        } catch (Exception ignored) {
        }
    }


    // ============================================================
    // 启动服务器
    // ============================================================

    private synchronized void startServers() {

        if (
                isAlive(dispatchProcess)
                        || isAlive(gameProcess)
        ) {

            appendLog(
                    "服务端已经在运行。"
            );

            return;
        }

        final String dispatch =
                nativePath(
                        "libdispatch.so"
                );

        final String gameserver =
                nativePath(
                        "libgameserver.so"
                );

        io.execute(() -> {

            try {

                serverRunning = true;

                statusText(
                        "正在启动 Dispatch..."
                );

                startButtonRunning(true);

                startButtonText(
                        "■  停止服务器"
                );

                dispatchProcess =
                        new ProcessBuilder(
                                dispatch
                        )
                                .directory(serverDir)
                                .redirectErrorStream(true)
                                .start();

                readOutput(
                        "Dispatch",
                        dispatchProcess.getInputStream()
                );

                Thread.sleep(800);

                if (
                        dispatchProcess != null
                                && dispatchProcess.isAlive()
                ) {

                    statusText(
                            "Dispatch 已启动，正在启动 GameServer..."
                    );

                    gameProcess =
                            new ProcessBuilder(
                                    gameserver
                            )
                                    .directory(serverDir)
                                    .redirectErrorStream(true)
                                    .start();

                    readOutput(
                            "GameServer",
                            gameProcess.getInputStream()
                    );

                    statusText(
                            "服务器运行中"
                    );

                    startButtonRunning(true);

                    startButtonText(
                            "■  停止服务器"
                    );

                } else {

                    serverRunning = false;

                    startButtonRunning(false);

                    startButtonText(
                            "▶  启动服务器"
                    );

                    statusText(
                            "服务器启动失败"
                    );

                    appendLog(
                            "Dispatch 已退出，"
                                    + "未启动 GameServer。"
                    );
                }

            } catch (Exception e) {

                serverRunning = false;

                startButtonRunning(false);

                startButtonText(
                        "▶  启动服务器"
                );

                statusText(
                        "启动失败"
                );

                appendLog(
                        "启动异常: " + e
                );
            }
        });
    }


    // ============================================================
    // 读取服务器日志
    // ============================================================

    private void readOutput(
            String tag,
            InputStream input
    ) {

        io.execute(() -> {

            try (
                    BufferedReader br =
                            new BufferedReader(
                                    new InputStreamReader(
                                            input
                                    )
                            )
            ) {

                String line;

                while (
                        (line = br.readLine())
                                != null
                ) {

                    appendLog(
                            "[" + tag + "] "
                                    + line
                    );
                }

            } catch (IOException e) {

                appendLog(
                        "[" + tag + "] "
                                + "日志读取结束: "
                                + e.getMessage()
                );
            }
        });
    }


    // ============================================================
    // 停止服务器
    // ============================================================

    private synchronized void stopServers() {

        if (gameProcess != null) {

            appendLog(
                    "停止 GameServer..."
            );

            gameProcess.destroy();

            gameProcess = null;
        }

        if (dispatchProcess != null) {

            appendLog(
                    "停止 Dispatch..."
            );

            dispatchProcess.destroy();

            dispatchProcess = null;
        }

        serverRunning = false;

        startButtonRunning(false);

        startButtonText(
                "▶  启动服务器"
        );

        statusText(
                "服务器已停止"
        );
    }


    private boolean isAlive(Process p) {

        return p != null
                && p.isAlive();
    }


    // ============================================================
    // GitHub 更新
    // ============================================================

    private void checkForUpdates(
            boolean manual
    ) {

        updateInfo.setText(
                "正在检查最新版本..."
        );

        io.execute(() -> {

            HttpURLConnection connection =
                    null;

            try {

                appendLog(
                        "正在检查 GitHub 最新版本..."
                );

                appendLog(
                        "仓库: "
                                + GITHUB_OWNER
                                + "/"
                                + GITHUB_REPO
                );

                URL url =
                        new URL(RELEASE_API);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        20000
                );

                connection.setInstanceFollowRedirects(
                        true
                );

                connection.setRequestMethod(
                        "GET"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                );

                connection.setRequestProperty(
                        "User-Agent",
                        "PearlSR-Android-Updater"
                );

                connection.setRequestProperty(
                        "X-GitHub-Api-Version",
                        "2022-11-28"
                );

                int code =
                        connection.getResponseCode();

                appendLog(
                        "GitHub API HTTP: "
                                + code
                );

                if (
                        code
                                != HttpURLConnection.HTTP_OK
                ) {

                    throw new IOException(
                            "GitHub API HTTP "
                                    + code
                    );
                }

                StringBuilder jsonText =
                        new StringBuilder();

                try (
                        InputStream input =
                                new BufferedInputStream(
                                        connection.getInputStream()
                                );

                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(
                                                input
                                        )
                                )
                ) {

                    String line;

                    while (
                            (line =
                                    reader.readLine())
                                    != null
                    ) {

                        jsonText.append(line);
                    }
                }

                /*
                 * 使用 Android 自带 JSONObject
                 * 不再使用脆弱的正则解析 GitHub JSON。
                 */

                JSONObject release =
                        new JSONObject(
                                jsonText.toString()
                        );

                String tag =
                        release.optString(
                                "tag_name",
                                null
                        );

                if (
                        tag == null
                                || tag.trim().isEmpty()
                ) {

                    throw new IOException(
                            "Release 没有 tag_name"
                    );
                }

                JSONArray assets =
                        release.optJSONArray(
                                "assets"
                        );

                if (
                        assets == null
                                || assets.length() == 0
                ) {

                    throw new IOException(
                            "最新 Release 没有附件"
                    );
                }

                String apkApiUrl = null;
                String apkBrowserUrl = null;
                String apkName = null;

                /*
                 * 在 assets 中寻找 APK。
                 */

                for (
                        int i = 0;
                        i < assets.length();
                        i++
                ) {

                    JSONObject asset =
                            assets.optJSONObject(i);

                    if (asset == null) {
                        continue;
                    }

                    String name =
                            asset.optString(
                                    "name",
                                    ""
                            );

                    if (
                            name.toLowerCase()
                                    .endsWith(".apk")
                    ) {

                        apkName = name;

                        apkApiUrl =
                                asset.optString(
                                        "url",
                                        null
                                );

                        apkBrowserUrl =
                                asset.optString(
                                        "browser_download_url",
                                        null
                                );

                        break;
                    }
                }

                if (
                        apkApiUrl == null
                                || apkApiUrl.trim().isEmpty()
                ) {

                    throw new IOException(
                            "最新 Release 中没有找到 APK Asset"
                    );
                }

                latestVersion =
                        normalizeVersion(tag);

                latestApkApiUrl =
                        apkApiUrl;

                latestApkBrowserUrl =
                        apkBrowserUrl;

                final String finalApkName =
                        apkName;

                final boolean hasUpdate =
                        compareVersions(
                                latestVersion,
                                BuildConfig.VERSION_NAME
                        ) > 0;

                appendLog(
                        "最新版本: "
                                + latestVersion
                );

                appendLog(
                        "APK: "
                                + finalApkName
                );

                appendLog(
                        "Asset API: "
                                + latestApkApiUrl
                );

                if (
                        latestApkBrowserUrl != null
                ) {

                    appendLog(
                            "Browser URL: "
                                    + latestApkBrowserUrl
                    );
                }

                main.post(() -> {

                    if (hasUpdate) {

                        updateInfo.setText(
                                "发现新版本 "
                                        + latestVersion
                                        + " · 正在下载"
                        );

                        appendLog(
                                "发现新版本: "
                                        + latestVersion
                        );

                        /*
                         * 直接开始下载
                         */

                        io.execute(
                                this::downloadApk
                        );

                    } else {

                        updateInfo.setText(
                                "当前已是最新版本 "
                                        + BuildConfig.VERSION_NAME
                        );

                        appendLog(
                                "当前已是最新版本: "
                                        + BuildConfig.VERSION_NAME
                        );

                        new AlertDialog.Builder(this)

                                .setTitle(
                                        "已是最新版本"
                                )

                                .setMessage(
                                        "当前版本："
                                                + BuildConfig.VERSION_NAME
                                                + "\n\n"
                                                + "暂时没有可用的新版本。"
                                )

                                .setPositiveButton(
                                        "确定",
                                        null
                                )

                                .show();
                    }
                });

            } catch (Exception e) {

                String error =
                        e.getClass()
                                .getSimpleName()
                                + ": "
                                + e.getMessage();

                appendLog(
                        "检查更新失败: "
                                + error
                );

                main.post(() ->
                        updateInfo.setText(
                                "检查更新失败 · 请重试"
                        )
                );

                if (manual) {

                    showToast(
                            "检查更新失败: "
                                    + e.getMessage()
                    );
                }

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }


    // ============================================================
    // 下载 APK
    // ============================================================

    private void downloadApk() {

        if (
                latestVersion == null
                        || latestVersion.trim().isEmpty()
        ) {

            showToast(
                    "还没有获取到最新版本信息"
            );

            return;
        }

        if (
                latestApkApiUrl == null
                        || latestApkApiUrl.trim().isEmpty()
        ) {

            showToast(
                    "还没有获取到 APK 下载地址"
            );

            return;
        }

        File external =
                getExternalFilesDir(null);

        if (external == null) {

            showToast(
                    "无法访问应用存储目录"
            );

            return;
        }

        File updateDir =
                new File(
                        external,
                        "update"
                );

        if (
                !updateDir.exists()
                        && !updateDir.mkdirs()
        ) {

            showToast(
                    "无法创建更新目录"
            );

            return;
        }

        File apk =
                new File(
                        updateDir,
                        "pearl-sr-"
                                + latestVersion
                                + "-update.apk"
                );

        /*
         * 删除旧 APK，防止断点/残留文件误安装。
         */

        if (apk.exists()) {

            if (!apk.delete()) {

                appendLog(
                        "警告：无法删除旧 APK"
                );
            }
        }

        statusText(
                "正在下载 "
                        + latestVersion
                        + "..."
        );

        appendLog(
                "开始下载 "
                        + latestVersion
        );

        /*
         * ========================================================
         * 第一次：
         * GitHub Release Asset API
         * ========================================================
         */

        Exception firstError = null;

        try {

            downloadFromUrl(
                    latestApkApiUrl,
                    apk,
                    true
            );

            /*
             * 下载成功
             */

            if (
                    apk.exists()
                            && apk.length() > 0
            ) {

                finishApkDownload(apk);

                return;
            }

        } catch (Exception e) {

            firstError = e;

            appendLog(
                    "Asset API 下载失败: "
                            + e.getClass()
                                    .getSimpleName()
                            + ": "
                            + e.getMessage()
            );
        }

        /*
         * ========================================================
         * 第二次：
         * browser_download_url
         * ========================================================
         */

        if (
                latestApkBrowserUrl != null
                        && !latestApkBrowserUrl.trim()
                        .isEmpty()
        ) {

            appendLog(
                    "正在尝试备用下载地址..."
            );

            if (apk.exists()) {

                //noinspection ResultOfMethodCallIgnored
                apk.delete();
            }

            try {

                downloadFromUrl(
                        latestApkBrowserUrl,
                        apk,
                        false
                );

                if (
                        apk.exists()
                                && apk.length() > 0
                ) {

                    finishApkDownload(apk);

                    return;
                }

            } catch (Exception e) {

                appendLog(
                        "备用下载也失败: "
                                + e.getClass()
                                        .getSimpleName()
                                + ": "
                                + e.getMessage()
                );
            }
        }

        /*
         * ========================================================
         * 全部失败
         * ========================================================
         */

        if (apk.exists()) {

            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }

        statusText(
                "更新失败"
        );

        String message =
                firstError != null
                        ? firstError.getMessage()
                        : "无法连接 GitHub";

        showToast(
                "更新失败: " + message
        );

        appendLog(
                "更新失败，请检查网络或 GitHub 连接。"
        );
    }


    // ============================================================
    // 实际下载
    // ============================================================

    private void downloadFromUrl(
            String downloadUrl,
            File apk,
            boolean assetApi
    ) throws Exception {

        Exception lastException = null;

        /*
         * 最多尝试 3 次
         */

        for (
                int attempt = 1;
                attempt <= 3;
                attempt++
        ) {

            HttpURLConnection connection =
                    null;

            try {

                appendLog(
                        "下载尝试 "
                                + attempt
                                + "/3"
                );

                URL url =
                        new URL(downloadUrl);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setConnectTimeout(
                        20000
                );

                connection.setReadTimeout(
                        60000
                );

                /*
                 * 允许 GitHub 302 跳转
                 */

                connection.setInstanceFollowRedirects(
                        true
                );

                connection.setRequestMethod(
                        "GET"
                );

                connection.setRequestProperty(
                        "User-Agent",
                        "PearlSR-Android-Updater"
                );

                if (assetApi) {

                    connection.setRequestProperty(
                            "Accept",
                            "application/octet-stream"
                    );

                    connection.setRequestProperty(
                            "X-GitHub-Api-Version",
                            "2022-11-28"
                    );

                } else {

                    connection.setRequestProperty(
                            "Accept",
                            "*/*"
                    );
                }

                int code =
                        connection.getResponseCode();

                appendLog(
                        "下载 HTTP 状态: "
                                + code
                );

                if (
                        code != HttpURLConnection.HTTP_OK
                ) {

                    throw new IOException(
                            "HTTP " + code
                    );
                }

                int total =
                        connection.getContentLength();

                try (
                        InputStream input =
                                new BufferedInputStream(
                                        connection.getInputStream()
                                );

                        OutputStream output =
                                new BufferedOutputStream(
                                        new FileOutputStream(
                                                apk
                                        )
                                )
                ) {

                    byte[] buffer =
                            new byte[1024 * 64];

                    long downloaded = 0;

                    int length;

                    while (
                            (length =
                                    input.read(buffer))
                                    != -1
                    ) {

                        output.write(
                                buffer,
                                0,
                                length
                        );

                        downloaded += length;

                        final long currentDownloaded =
                                downloaded;

                        if (total > 0) {

                            int progress =
                                    (int) (
                                            currentDownloaded
                                                    * 100L
                                                    / total
                                    );

                            main.post(() ->
                                    statusText(
                                            "正在下载 "
                                                    + latestVersion
                                                    + " · "
                                                    + progress
                                                    + "%"
                                    )
                            );

                        } else {

                            long mb =
                                    currentDownloaded
                                            / 1024
                                            / 1024;

                            main.post(() ->
                                    statusText(
                                            "正在下载 "
                                                    + latestVersion
                                                    + " · "
                                                    + mb
                                                    + " MB"
                                    )
                            );
                        }
                    }
                }

                /*
                 * 确保数据真正写入磁盘
                 */

                if (
                        !apk.exists()
                                || apk.length() <= 0
                ) {
                    throw new IOException("下载完成但 APK 文件为空");
                }

                // GitHub error pages can otherwise be saved as .apk files.
                try (RandomAccessFile apkFile = new RandomAccessFile(apk, "r")) {
                    if (apkFile.read() != 'P' || apkFile.read() != 'K'
                            || apkFile.read() != 3 || apkFile.read() != 4) {
                        throw new IOException("下载内容不是有效 APK");
                    }
                }

                appendLog(
                        "下载文件大小: "
                                + apk.length()
                                + " bytes"
                );

                return;

            } catch (Exception e) {

                lastException = e;

                /*
                 * 删除失败下载产生的残留文件
                 */

                if (apk.exists()) {

                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                }

                /*
                 * 最后一次不要等待
                 */

                if (attempt < 3) {

                    appendLog(
                            "下载失败，"
                                    + "2 秒后重试..."
                    );

                    try {

                        Thread.sleep(2000);

                    } catch (InterruptedException interrupted) {

                        Thread.currentThread()
                                .interrupt();

                        throw interrupted;
                    }
                }

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        throw new IOException(
                "下载失败"
        );
    }


    // ============================================================
    // APK 下载完成
    // ============================================================

    private void finishApkDownload(
            File apk
    ) {

        appendLog(
                "APK 下载完成: "
                        + apk.length()
                        + " bytes"
        );

        statusText(
                "更新下载完成"
        );

        main.post(
                () -> installApk(apk)
        );
    }


    // ============================================================
    // 安装 APK
    // ============================================================

    private void installApk(
            File apk
    ) {

        try {

            if (
                    apk == null
                            || !apk.exists()
                            || apk.length() == 0
            ) {

                throw new IOException(
                        "APK 文件不存在或为空"
                );
            }

            appendLog(
                    "准备安装 APK: "
                            + apk.getAbsolutePath()
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getPackageManager().canRequestPackageInstalls()) {
                Intent settingsIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                showToast("请允许本应用安装未知应用，然后重新点击更新");
                return;
            }

            Uri uri =
                    FileProvider.getUriForFile(
                            this,
                            getPackageName()
                                    + ".fileprovider",
                            apk
                    );

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW
                    );

            intent.setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            startActivity(intent);

        } catch (Exception e) {

            appendLog(
                    "无法打开 APK 安装程序: "
                            + e
            );

            showToast(
                    "无法安装 APK，请检查系统的"
                            + "“允许安装未知应用”权限。"
            );
        }
    }


    // ============================================================
    // 版本号处理
    // ============================================================

    private String normalizeVersion(
            String value
    ) {

        String v =
                value == null
                        ? ""
                        : value.trim();

        while (
                v.startsWith("v")
                        || v.startsWith("V")
        ) {

            v = v.substring(1);
        }

        int space =
                v.indexOf(' ');

        if (space > 0) {
            v = v.substring(
                    0,
                    space
            );
        }

        return v;
    }


    private int compareVersions(
            String a,
            String b
    ) {

        String[] pa =
                normalizeVersion(a)
                        .split("\\.");

        String[] pb =
                normalizeVersion(b)
                        .split("\\.");

        int length =
                Math.max(
                        pa.length,
                        pb.length
                );

        for (
                int i = 0;
                i < length;
                i++
        ) {

            int va =
                    i < pa.length
                            ? numericPart(pa[i])
                            : 0;

            int vb =
                    i < pb.length
                            ? numericPart(pb[i])
                            : 0;

            if (va != vb) {

                return Integer.compare(
                        va,
                        vb
                );
            }
        }

        return 0;
    }


    private int numericPart(
            String value
    ) {

        if (
                value == null
                        || value.isEmpty()
        ) {
            return 0;
        }

        StringBuilder digits =
                new StringBuilder();

        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            char c =
                    value.charAt(i);

            if (
                    c >= '0'
                            && c <= '9'
            ) {

                digits.append(c);

            } else {

                break;
            }
        }

        if (digits.length() == 0) {
            return 0;
        }

        try {

            return Integer.parseInt(
                    digits.toString()
            );

        } catch (NumberFormatException e) {

            return 0;
        }
    }


    // ============================================================
    // UI
    // ============================================================

    private void showToast(
            String text
    ) {

        main.post(() ->
                Toast.makeText(
                        this,
                        text,
                        Toast.LENGTH_LONG
                ).show()
        );
    }


    private void statusText(
            String text
    ) {

        main.post(() ->
                status.setText(text)
        );
    }


    private void startButtonText(
            String text
    ) {

        main.post(() ->
                startButton.setText(text)
        );
    }


    private void startButtonRunning(
            boolean running
    ) {

        main.post(() ->
                startButton.setBackgroundResource(
                        running
                                ? R.drawable.bg_start_button_running
                                : R.drawable.bg_start_button
                )
        );
    }


    private void appendLog(
            String text
    ) {

        main.post(() -> {

            logView.append(
                    text + "\n"
            );

            if (
                    logScroll.getVisibility()
                            == View.VISIBLE
            ) {

                logContentScroll.post(
                        () -> logContentScroll.fullScroll(
                                ScrollView.FOCUS_DOWN
                        )
                );
            }
        });
    }


    // ============================================================
    // 销毁
    // ============================================================

    @Override
    protected void onDestroy() {

        stopServers();

        io.shutdownNow();

        super.onDestroy();
    }
}