package com.reversedrooms.pearlserver;

import android.Manifest;
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
    private TextView startButton;
    private TextView updateInfo;
    private View statusDot;

    private final Handler main =
            new Handler(Looper.getMainLooper());

    private final ExecutorService io =
            Executors.newFixedThreadPool(3);

    private Process dispatchProcess;
    private Process gameProcess;

    private File serverDir;

    private static final int STORAGE_PERMISSION_REQUEST = 2001;
    private boolean storagePromptReady;
    private boolean storagePreparing;
    private boolean serverFilesReady;

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
        startButton = findViewById(R.id.startButton);
        updateInfo = findViewById(R.id.updateInfo);
        statusDot = findViewById(R.id.statusDot);

        /*
         * ========================================================
         * 准备服务器文件
         * ========================================================
         */

        statusText("等待准备服务端文件");


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
         * SRTools
         * ========================================================
         */

        View srToolsButton =
                findViewById(R.id.srToolsButton);

        srToolsButton.setOnClickListener(v -> {
            if (!ServerStorage.hasAccess(this) || !serverFilesReady) {
                ensureServerStorage(true);
                return;
            }

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

        showSecurityWarning(() -> {
            storagePromptReady = true;
            ensureServerStorage(true);
        });
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
                    return;
                } catch (Exception ignored) {
                    // Some vendor ROMs do not expose the app detail battery page.
                }

                Intent batteryIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(batteryIntent);
                prefsMarkBackgroundAsked();
                return;
            }

        } catch (Exception e) {

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

    @Override
    protected void onResume() {
        super.onResume();
        if (storagePromptReady && ServerStorage.hasAccess(this)) {
            ensureServerStorage(false);
        } else if (storagePromptReady && serverFilesReady) {
            serverFilesReady = false;
            stopServers();
            statusText("需要存储权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (ServerStorage.hasAccess(this)) {
                ensureServerStorage(false);
            } else {
                statusText("需要存储权限，点击启动重试");
            }
        }
    }

    private void ensureServerStorage(boolean requestPermission) {
        if (!ServerStorage.hasAccess(this)) {
            serverFilesReady = false;
            statusText("需要存储权限");
            if (requestPermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    new AlertDialog.Builder(this)
                            .setTitle("允许访问服务端文件")
                            .setMessage("服务端需要读写 Download/Pearl SR 中的资源和同步数据。"
                                    + "请在系统设置中允许本应用访问所有文件。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("去授权", (dialog, which) -> {
                                try {
                                    startActivity(new Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:" + getPackageName())));
                                } catch (Exception e) {
                                    try {
                                        startActivity(new Intent(
                                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                                    } catch (Exception unavailable) {
                                        showToast("请在系统设置中允许本应用访问所有文件");
                                    }
                                }
                            }).show();
                } else {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, STORAGE_PERMISSION_REQUEST);
                }
            }
            return;
        }
        if (serverFilesReady || storagePreparing) {
            return;
        }
        storagePreparing = true;
        statusText("正在准备服务端文件...");
        io.execute(() -> {
            try {
                File preparedDirectory = ServerStorage.prepare(this);
                setExecutable(new File(nativePath("libdispatch.so")));
                setExecutable(new File(nativePath("libgameserver.so")));
                main.post(() -> {
                    storagePreparing = false;
                    if (isDestroyed()) return;
                    serverDir = preparedDirectory;
                    serverFilesReady = true;
                    statusText("服务端文件已准备");
                    showBackgroundPermissionIfNeeded();
                });
            } catch (Exception e) {
                main.post(() -> {
                    storagePreparing = false;
                    serverFilesReady = false;
                    if (isDestroyed()) return;
                    statusText("文件初始化失败，点击启动重试");
                    showToast("初始化失败: " + e.getMessage());
                });
            }
        });
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
        if (!ServerStorage.hasAccess(this) || !serverFilesReady) {
            ensureServerStorage(true);
            return;
        }

        if (
                isAlive(dispatchProcess)
                        || isAlive(gameProcess)
        ) {


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
                                .redirectOutput(new File("/dev/null"))
                                .start();


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
                                .redirectOutput(new File("/dev/null"))
                                    .start();


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

                showToast("启动失败: " + e.getMessage());
            }
        });
    }


    // ============================================================
    // 停止服务器
    // ============================================================

    private synchronized void stopServers() {

        if (gameProcess != null) {


            gameProcess.destroy();

            gameProcess = null;
        }

        if (dispatchProcess != null) {


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

                final boolean hasUpdate =
                        compareVersions(
                                latestVersion,
                                BuildConfig.VERSION_NAME
                        ) > 0;


                main.post(() -> {

                    if (hasUpdate) {

                        updateInfo.setText(
                                "发现新版本 "
                                        + latestVersion
                                        + " · 正在下载"
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

            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }

        statusText(
                "正在下载 "
                        + latestVersion
                        + "..."
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

        main.post(() -> {
            startButton.setBackgroundResource(
                    running
                            ? R.drawable.bg_start_button_running
                            : R.drawable.bg_start_button
            );
            statusDot.setBackgroundResource(
                    running
                            ? R.drawable.bg_dot
                            : R.drawable.bg_dot_inactive
            );
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