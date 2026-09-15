package com.reversedrooms.pearlserver;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class SRToolsActivity extends Activity {

    private static final String URL =
            "https://srtools.neonteam.dev/";

    /*
     * 公共下载目录：
     *
     * /storage/emulated/0/Download/Pearl SR/
     */
    private static final String DOWNLOAD_FOLDER =
            "Pearl SR";

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int WRITE_PERMISSION_REQUEST = 1002;

    private WebView webView;
    private ProgressBar progressBar;

    /*
     * 网页文件选择器
     */
    private ValueCallback<Uri[]> filePathCallback;

    /*
     * Blob 下载输出流
     */
    private OutputStream blobOutputStream;

    /*
     * Android 9 及以下 Blob 文件
     */
    private File blobTempFile;

    /*
     * Android 10+ Blob MediaStore URI
     */
    private Uri blobMediaStoreUri;

    private String blobFileName;
    private String blobMimeType;

    /*
     * Blob JS 是否已经注入
     */
    private boolean blobInterceptorInstalled = false;


    // ============================================================
    // Activity
    // ============================================================

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.activity_srtools);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        /*
         * 启动时准备 Pearl SR 下载目录
         */
        createPearlDownloadDirectory();

        setupWebView();

        /*
         * 刷新
         */
        findViewById(R.id.reloadButton).setOnClickListener(v -> {

            blobInterceptorInstalled = false;

            webView.reload();
        });

        /*
         * 关闭
         */
        findViewById(R.id.closeButton).setOnClickListener(
                v -> finish()
        );

        /*
         * 加载 SRTools
         */
        webView.loadUrl(URL);
    }


    // ============================================================
    // WebView 设置
    // ============================================================

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setLoadsImagesAutomatically(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        /*
         * 允许 HTTPS 页面访问 HTTP 服务
         *
         * 例如：
         *
         * http://localhost:21000
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }


        // ========================================================
        // WebViewClient
        // ========================================================

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                return false;
            }


            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {

                super.onPageStarted(
                        view,
                        url,
                        favicon
                );

                progressBar.setVisibility(View.VISIBLE);

                injectBlobInterceptor(view);
            }


            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                super.onPageFinished(
                        view,
                        url
                );

                progressBar.setVisibility(View.GONE);

                injectBlobInterceptor(view);
            }
        });


        // ========================================================
        // WebChromeClient
        // ========================================================

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress
            ) {

                super.onProgressChanged(
                        view,
                        newProgress
                );

                if (newProgress >= 100) {

                    progressBar.setVisibility(
                            View.GONE
                    );

                } else {

                    progressBar.setVisibility(
                            View.VISIBLE
                    );

                    progressBar.setProgress(
                            newProgress
                    );
                }
            }


            // ====================================================
            // 网页上传文件
            // ====================================================

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {

                /*
                 * 取消上一个文件选择请求
                 */
                if (SRToolsActivity.this.filePathCallback != null) {

                    SRToolsActivity.this.filePathCallback
                            .onReceiveValue(null);
                }

                SRToolsActivity.this.filePathCallback =
                        filePathCallback;

                try {

                    Intent intent =
                            fileChooserParams.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (Exception e) {

                    SRToolsActivity.this.filePathCallback =
                            null;

                    Toast.makeText(
                            SRToolsActivity.this,
                            "无法打开文件选择器：" +
                                    e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }

                return true;
            }
        });


        // ========================================================
        // 下载监听
        // ========================================================

        webView.setDownloadListener(
                (
                        downloadUrl,
                        userAgent,
                        contentDisposition,
                        mimeType,
                        contentLength
                ) -> {

                    handleDownload(
                            downloadUrl,
                            userAgent,
                            contentDisposition,
                            mimeType
                    );
                }
        );


        // ========================================================
        // JavaScript Bridge
        // ========================================================

        webView.addJavascriptInterface(
                new BlobDownloadBridge(),
                "PearlDownload"
        );
    }


    // ============================================================
    // 创建 Pearl SR 下载目录
    // ============================================================

    private void createPearlDownloadDirectory() {

        /*
         * Android 10+
         *
         * MediaStore 本身不提供直接 mkdir。
         *
         * 因此这里创建一个隐藏的占位文件，
         * 让系统实际建立：
         *
         * Download/Pearl SR/
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            try {

                ContentValues values =
                        new ContentValues();

                values.put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        ".pearl_sr"
                );

                values.put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/octet-stream"
                );

                values.put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                                + "/"
                                + DOWNLOAD_FOLDER
                );

                values.put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                );

                Uri uri =
                        getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                        );

                if (uri != null) {

                    try {

                        OutputStream output =
                                getContentResolver()
                                        .openOutputStream(uri);

                        if (output != null) {

                            output.close();
                        }

                    } catch (Exception ignored) {
                    }


                    /*
                     * 占位文件完成
                     */
                    ContentValues completed =
                            new ContentValues();

                    completed.put(
                            MediaStore.Downloads.IS_PENDING,
                            0
                    );

                    getContentResolver().update(
                            uri,
                            completed,
                            null,
                            null
                    );
                }

            } catch (Exception e) {

                /*
                 * 目录创建失败不阻止 App 启动。
                 * 真正下载时还会再次创建。
                 */
            }

            return;
        }


        /*
         * Android 9 及以下
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE
                        },
                        WRITE_PERMISSION_REQUEST
                );

                return;
            }
        }


        try {

            File directory =
                    getLegacyDownloadDirectory();

            if (!directory.exists()) {

                if (!directory.mkdirs() &&
                        !directory.exists()) {

                    Toast.makeText(
                            this,
                            "无法创建 Pearl SR 下载目录",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "创建下载目录失败：" +
                            e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    // ============================================================
    // 获取传统下载目录
    // ============================================================

    private File getLegacyDownloadDirectory() {

        File downloadDirectory =
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                );

        return new File(
                downloadDirectory,
                DOWNLOAD_FOLDER
        );
    }


    // ============================================================
    // 下载处理
    // ============================================================

    private void handleDownload(
            String downloadUrl,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {

        if (downloadUrl == null ||
                downloadUrl.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "下载地址为空",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        /*
         * Blob
         */
        if (downloadUrl.startsWith("blob:")) {

            String escapedUrl =
                    escapeJs(downloadUrl);

            String escapedMime =
                    escapeJs(
                            mimeType == null
                                    ? "application/octet-stream"
                                    : mimeType
                    );

            String javascript =
                    "window.__pearlDownloadBlob(" +
                            "'" +
                            escapedUrl +
                            "'," +
                            "'" +
                            escapedMime +
                            "'" +
                            ");";

            webView.evaluateJavascript(
                    javascript,
                    null
            );

            return;
        }


        /*
         * HTTP / HTTPS
         */
        if (downloadUrl.startsWith("http://") ||
                downloadUrl.startsWith("https://")) {

            downloadHttpFile(
                    downloadUrl,
                    userAgent,
                    contentDisposition,
                    mimeType
            );

            return;
        }


        /*
         * 其他 URI
         */
        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(downloadUrl)
                    );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "无法处理下载地址",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // Blob JavaScript 拦截器
    // ============================================================

    private void injectBlobInterceptor(
            WebView view
    ) {

        if (view == null) {
            return;
        }


        /*
         * 页面刷新后需要重新注入。
         */
        if (blobInterceptorInstalled) {
            return;
        }

        blobInterceptorInstalled = true;


        String javascript =
                "(function() {" +

                "if (window.__pearlBlobInstalled) return;" +

                "window.__pearlBlobInstalled = true;" +

                "window.__pearlBlobMap = new Map();" +

                "" +

                "const originalCreateObjectURL =" +
                "URL.createObjectURL;" +

                "const originalRevokeObjectURL =" +
                "URL.revokeObjectURL;" +

                "" +

                "URL.createObjectURL = function(blob) {" +

                "    const url =" +
                "        originalCreateObjectURL.call(URL, blob);" +

                "    try {" +
                "        window.__pearlBlobMap.set(url, blob);" +
                "    } catch(e) {}" +

                "    return url;" +

                "};" +

                "" +

                "URL.revokeObjectURL = function(url) {" +

                "    try {" +

                "        setTimeout(function() {" +

                "            try {" +
                "                window.__pearlBlobMap.delete(url);" +
                "            } catch(e) {}" +

                "        }, 10000);" +

                "    } catch(e) {}" +

                "    try {" +

                "        return originalRevokeObjectURL.call(" +
                "            URL," +
                "            url" +
                "        );" +

                "    } catch(e) {}" +

                "};" +

                "" +

                "window.__pearlDownloadBlob =" +
                "async function(url, mimeType) {" +

                "    try {" +

                "        let blob =" +
                "            window.__pearlBlobMap.get(url);" +

                "" +

                "        if (!blob) {" +

                "            try {" +

                "                const response =" +
                "                    await fetch(url);" +

                "                blob =" +
                "                    await response.blob();" +

                "            } catch(e) {}" +

                "        }" +

                "" +

                "        if (!blob) {" +

                "            throw new Error(" +
                "'找不到 Blob 对象，可能网页已经释放 Blob URL'" +
                ");" +

                "        }" +

                "" +

                "        const finalMime =" +
                "            mimeType ||" +
                "            blob.type ||" +
                "'application/octet-stream';" +

                "" +

                "        let fileName =" +
                "            'download.bin';" +

                "" +

                "        try {" +

                "            const links =" +
                "                document.querySelectorAll('a');" +

                "            for (" +
                "                let i = 0;" +
                "                i < links.length;" +
                "                i++" +
                "            ) {" +

                "                const a = links[i];" +

                "                if (" +
                "                    a.href === url &&" +
                "                    a.download" +
                "                ) {" +

                "                    fileName =" +
                "                        a.download;" +

                "                    break;" +

                "                }" +
                "            }" +

                "        } catch(e) {}" +

                "" +

                "        window.PearlDownload.beginBlob(" +
                "            fileName," +
                "            finalMime," +
                "            blob.size" +
                "        );" +

                "" +

                "        const chunkSize =" +
                "            256 * 1024;" +

                "        let offset = 0;" +

                "" +

                "        while (offset < blob.size) {" +

                "            const chunk =" +
                "                blob.slice(" +
                "                    offset," +
                "                    Math.min(" +
                "                        offset + chunkSize," +
                "                        blob.size" +
                "                    )" +
                "                );" +

                "" +

                "            const buffer =" +
                "                await chunk.arrayBuffer();" +

                "" +

                "            const bytes =" +
                "                new Uint8Array(buffer);" +

                "" +

                "            let binary = '';" +

                "            const blockSize = 8192;" +

                "" +

                "            for (" +
                "                let i = 0;" +
                "                i < bytes.length;" +
                "                i += blockSize" +
                "            ) {" +

                "                const sub =" +
                "                    bytes.subarray(" +
                "                        i," +
                "                        Math.min(" +
                "                            i + blockSize," +
                "                            bytes.length" +
                "                        )" +
                "                    );" +

                "                binary +=" +
                "                    String.fromCharCode.apply(" +
                "                        null," +
                "                        sub" +
                "                    );" +

                "            }" +

                "" +

                "            const base64 =" +
                "                btoa(binary);" +

                "" +

                "            window.PearlDownload" +
                "                .receiveBlobChunk(base64);" +

                "" +

                "            offset +=" +
                "                bytes.length;" +

                "        }" +

                "" +

                "        window.PearlDownload.finishBlob();" +

                "" +

                "    } catch(e) {" +

                "        window.PearlDownload.error(" +
                "            String(e)" +
                "        );" +

                "    }" +

                "};" +

                "})();";


        view.evaluateJavascript(
                javascript,
                null
        );
    }


    // ============================================================
    // Blob Bridge
    // ============================================================

    private class BlobDownloadBridge {


        @android.webkit.JavascriptInterface
        public void beginBlob(
                String fileName,
                String mimeType,
                long size
        ) {

            runOnUiThread(() -> {

                try {

                    cleanupBlobDownload();

                    blobFileName =
                            sanitizeFileName(
                                    fileName
                            );

                    blobMimeType =
                            mimeType;


                    if (blobFileName == null ||
                            blobFileName.trim().isEmpty()) {

                        blobFileName =
                                "download.bin";
                    }


                    if (blobMimeType == null ||
                            blobMimeType.trim().isEmpty()) {

                        blobMimeType =
                                "application/octet-stream";
                    }


                    /*
                     * Android 10+
                     */
                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.Q) {

                        ContentValues values =
                                new ContentValues();

                        values.put(
                                MediaStore.Downloads.DISPLAY_NAME,
                                blobFileName
                        );

                        values.put(
                                MediaStore.Downloads.MIME_TYPE,
                                blobMimeType
                        );

                        values.put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS
                                        + "/"
                                        + DOWNLOAD_FOLDER
                        );

                        values.put(
                                MediaStore.Downloads.IS_PENDING,
                                1
                        );


                        blobMediaStoreUri =
                                getContentResolver().insert(
                                        MediaStore.Downloads
                                                .EXTERNAL_CONTENT_URI,
                                        values
                                );


                        if (blobMediaStoreUri == null) {

                            throw new IOException(
                                    "无法创建下载文件"
                            );
                        }


                        blobOutputStream =
                                getContentResolver()
                                        .openOutputStream(
                                                blobMediaStoreUri
                                        );


                        if (blobOutputStream == null) {

                            throw new IOException(
                                    "无法打开下载文件"
                            );
                        }

                    } else {

                        /*
                         * Android 9 及以下
                         */
                        if (Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.M) {

                            if (ContextCompat.checkSelfPermission(
                                    SRToolsActivity.this,
                                    Manifest.permission
                                            .WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED) {

                                requestPermissions(
                                        new String[]{
                                                Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE
                                        },
                                        WRITE_PERMISSION_REQUEST
                                );

                                throw new IOException(
                                        "需要存储权限"
                                );
                            }
                        }


                        File downloadDir =
                                getLegacyDownloadDirectory();


                        if (!downloadDir.exists()) {

                            if (!downloadDir.mkdirs() &&
                                    !downloadDir.exists()) {

                                throw new IOException(
                                        "无法创建下载目录"
                                );
                            }
                        }


                        File outputFile =
                                getUniqueFile(
                                        downloadDir,
                                        blobFileName
                                );


                        blobTempFile =
                                outputFile;


                        blobOutputStream =
                                new FileOutputStream(
                                        outputFile
                                );
                    }


                } catch (Exception e) {

                    cleanupBlobDownload();

                    Toast.makeText(
                            SRToolsActivity.this,
                            "开始下载失败：" +
                                    e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }


        @android.webkit.JavascriptInterface
        public void receiveBlobChunk(
                String base64
        ) {

            try {

                if (blobOutputStream == null) {

                    throw new IOException(
                            "下载文件没有打开"
                    );
                }


                byte[] data =
                        Base64.getDecoder().decode(
                                base64
                        );


                blobOutputStream.write(
                        data
                );

            } catch (Exception e) {

                runOnUiThread(() -> {

                    cleanupBlobDownload();

                    Toast.makeText(
                            SRToolsActivity.this,
                            "写入下载文件失败：" +
                                    e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }


        @android.webkit.JavascriptInterface
        public void finishBlob() {

            runOnUiThread(() -> {

                try {

                    if (blobOutputStream != null) {

                        blobOutputStream.flush();
                        blobOutputStream.close();

                        blobOutputStream = null;
                    }


                    /*
                     * Android 10+
                     */
                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.Q) {

                        if (blobMediaStoreUri != null) {

                            ContentValues values =
                                    new ContentValues();

                            values.put(
                                    MediaStore.Downloads.IS_PENDING,
                                    0
                            );

                            getContentResolver().update(
                                    blobMediaStoreUri,
                                    values,
                                    null,
                                    null
                            );
                        }
                    }


                    /*
                     * Lambda 不能直接捕获后续会变化的字段。
                     *
                     * 这里复制成 final。
                     */
                    final String completedFileName =
                            blobFileName;


                    Toast.makeText(
                            SRToolsActivity.this,
                            "下载完成\n" +
                                    "Download/Pearl SR/" +
                                    completedFileName,
                            Toast.LENGTH_LONG
                    ).show();


                    blobTempFile = null;
                    blobMediaStoreUri = null;
                    blobFileName = null;
                    blobMimeType = null;


                } catch (Exception e) {

                    cleanupBlobDownload();

                    Toast.makeText(
                            SRToolsActivity.this,
                            "完成下载失败：" +
                                    e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }


        @android.webkit.JavascriptInterface
        public void error(
                String message
        ) {

            runOnUiThread(() -> {

                cleanupBlobDownload();

                Toast.makeText(
                        SRToolsActivity.this,
                        "Blob 下载失败：" +
                                message,
                        Toast.LENGTH_LONG
                ).show();
            });
        }
    }


    // ============================================================
    // HTTP / HTTPS 下载
    // ============================================================

    private void downloadHttpFile(
            String downloadUrl,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                URL url =
                        new URL(downloadUrl);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod(
                        "GET"
                );

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        60000
                );

                connection.setInstanceFollowRedirects(
                        true
                );


                if (userAgent != null &&
                        !userAgent.isEmpty()) {

                    connection.setRequestProperty(
                            "User-Agent",
                            userAgent
                    );
                }


                int responseCode =
                        connection.getResponseCode();


                if (responseCode < 200 ||
                        responseCode >= 300) {

                    throw new IOException(
                            "HTTP " +
                                    responseCode
                    );
                }


                String fileName =
                        extractFileName(
                                contentDisposition
                        );


                if (fileName == null ||
                        fileName.isEmpty()) {

                    fileName =
                            extractFileNameFromUrl(
                                    downloadUrl
                            );
                }


                if (fileName == null ||
                        fileName.isEmpty()) {

                    fileName =
                            "download.bin";
                }


                fileName =
                        sanitizeFileName(
                                fileName
                        );


                String finalMimeType =
                        mimeType;


                if (finalMimeType == null ||
                        finalMimeType.isEmpty()) {

                    finalMimeType =
                            "application/octet-stream";
                }


                /*
                 * Android 10+
                 */
                if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q) {

                    ContentValues values =
                            new ContentValues();

                    values.put(
                            MediaStore.Downloads.DISPLAY_NAME,
                            fileName
                    );

                    values.put(
                            MediaStore.Downloads.MIME_TYPE,
                            finalMimeType
                    );

                    values.put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS
                                    + "/"
                                    + DOWNLOAD_FOLDER
                    );

                    values.put(
                            MediaStore.Downloads.IS_PENDING,
                            1
                    );


                    Uri uri =
                            getContentResolver().insert(
                                    MediaStore.Downloads
                                            .EXTERNAL_CONTENT_URI,
                                    values
                            );


                    if (uri == null) {

                        throw new IOException(
                                "无法创建下载文件"
                        );
                    }


                    try (
                            InputStream input =
                                    connection.getInputStream();

                            OutputStream output =
                                    getContentResolver()
                                            .openOutputStream(uri)
                    ) {

                        if (output == null) {

                            throw new IOException(
                                    "无法打开输出文件"
                            );
                        }


                        copyStream(
                                input,
                                output
                        );
                    }


                    ContentValues completed =
                            new ContentValues();

                    completed.put(
                            MediaStore.Downloads.IS_PENDING,
                            0
                    );


                    getContentResolver().update(
                            uri,
                            completed,
                            null,
                            null
                    );


                } else {

                    /*
                     * Android 9 及以下
                     */
                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M) {

                        if (ContextCompat.checkSelfPermission(
                                SRToolsActivity.this,
                                Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED) {

                            runOnUiThread(() -> {

                                requestPermissions(
                                        new String[]{
                                                Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE
                                        },
                                        WRITE_PERMISSION_REQUEST
                                );
                            });

                            throw new IOException(
                                    "需要存储权限"
                            );
                        }
                    }


                    File directory =
                            getLegacyDownloadDirectory();


                    if (!directory.exists()) {

                        if (!directory.mkdirs() &&
                                !directory.exists()) {

                            throw new IOException(
                                    "无法创建下载目录"
                            );
                        }
                    }


                    File outputFile =
                            getUniqueFile(
                                    directory,
                                    fileName
                            );


                    try (
                            InputStream input =
                                    connection.getInputStream();

                            OutputStream output =
                                    new FileOutputStream(
                                            outputFile
                                    )
                    ) {

                        copyStream(
                                input,
                                output
                        );
                    }
                }


                /*
                 * 修复 Lambda effectively final 问题
                 */
                final String completedFileName =
                        fileName;


                runOnUiThread(() -> {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "下载完成\n" +
                                    "Download/Pearl SR/" +
                                    completedFileName,
                            Toast.LENGTH_LONG
                    ).show();
                });


            } catch (Exception e) {

                final String errorMessage =
                        e.getMessage() == null
                                ? e.toString()
                                : e.getMessage();


                runOnUiThread(() -> {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "下载失败：" +
                                    errorMessage,
                            Toast.LENGTH_LONG
                    ).show();
                });


            } finally {

                if (connection != null) {

                    connection.disconnect();
                }
            }

        }).start();
    }


    // ============================================================
    // Stream 复制
    // ============================================================

    private void copyStream(
            InputStream input,
            OutputStream output
    ) throws IOException {

        byte[] buffer =
                new byte[64 * 1024];

        int length;

        while ((length = input.read(buffer)) != -1) {

            output.write(
                    buffer,
                    0,
                    length
            );
        }

        output.flush();
    }


    // ============================================================
    // 同名文件处理
    // ============================================================

    private File getUniqueFile(
            File directory,
            String fileName
    ) {

        File file =
                new File(
                        directory,
                        fileName
                );


        if (!file.exists()) {

            return file;
        }


        String name =
                fileName;

        String extension =
                "";


        int dot =
                fileName.lastIndexOf('.');


        if (dot > 0) {

            name =
                    fileName.substring(
                            0,
                            dot
                    );

            extension =
                    fileName.substring(
                            dot
                    );
        }


        int index = 1;


        while (file.exists()) {

            file =
                    new File(
                            directory,
                            name +
                                    " (" +
                                    index +
                                    ")" +
                                    extension
                    );

            index++;
        }


        return file;
    }


    // ============================================================
    // Content-Disposition 文件名
    // ============================================================

    private String extractFileName(
            String contentDisposition
    ) {

        if (contentDisposition == null) {

            return null;
        }


        try {

            String lower =
                    contentDisposition.toLowerCase();


            int index =
                    lower.indexOf(
                            "filename="
                    );


            if (index >= 0) {

                String value =
                        contentDisposition.substring(
                                index +
                                        "filename=".length()
                        ).trim();


                if (value.startsWith("\"")) {

                    int end =
                            value.indexOf(
                                    "\"",
                                    1
                            );


                    if (end > 1) {

                        return value.substring(
                                1,
                                end
                        );
                    }
                }


                int semicolon =
                        value.indexOf(';');


                if (semicolon >= 0) {

                    value =
                            value.substring(
                                    0,
                                    semicolon
                            );
                }


                return value.trim();
            }

        } catch (Exception ignored) {
        }


        return null;
    }


    // ============================================================
    // URL 文件名
    // ============================================================

    private String extractFileNameFromUrl(
            String url
    ) {

        try {

            Uri uri =
                    Uri.parse(url);


            String path =
                    uri.getPath();


            if (path == null ||
                    path.isEmpty()) {

                return null;
            }


            int slash =
                    path.lastIndexOf('/');


            if (slash >= 0 &&
                    slash + 1 < path.length()) {

                return path.substring(
                        slash + 1
                );
            }


        } catch (Exception ignored) {
        }


        return null;
    }


    // ============================================================
    // 文件名清理
    // ============================================================

    private String sanitizeFileName(
            String fileName
    ) {

        if (fileName == null) {

            return "download.bin";
        }


        String result =
                fileName.trim();


        if (result.isEmpty()) {

            return "download.bin";
        }


        result =
                result.replace(
                        "/",
                        "_"
                );

        result =
                result.replace(
                        "\\",
                        "_"
                );

        result =
                result.replace(
                        ":",
                        "_"
                );

        result =
                result.replace(
                        "*",
                        "_"
                );

        result =
                result.replace(
                        "?",
                        "_"
                );

        result =
                result.replace(
                        "\"",
                        "_"
                );

        result =
                result.replace(
                        "<",
                        "_"
                );

        result =
                result.replace(
                        ">",
                        "_"
                );

        result =
                result.replace(
                        "|",
                        "_"
                );


        return result;
    }


    // ============================================================
    // JavaScript 字符串转义
    // ============================================================

    private String escapeJs(
            String value
    ) {

        if (value == null) {

            return "";
        }


        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "'",
                        "\\'"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "</",
                        "<\\/"
                );
    }


    // ============================================================
    // Blob 清理
    // ============================================================

    private void closeBlobOutput() {

        if (blobOutputStream != null) {

            try {

                blobOutputStream.close();

            } catch (Exception ignored) {
            }

            blobOutputStream = null;
        }
    }


    private void cleanupBlobDownload() {

        closeBlobOutput();


        /*
         * Android 10+
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            if (blobMediaStoreUri != null) {

                try {

                    getContentResolver().delete(
                            blobMediaStoreUri,
                            null,
                            null
                    );

                } catch (Exception ignored) {
                }
            }
        }


        /*
         * Android 9 及以下
         */
        if (blobTempFile != null) {

            try {

                if (blobTempFile.exists()) {

                    blobTempFile.delete();
                }

            } catch (Exception ignored) {
            }
        }


        blobTempFile = null;
        blobMediaStoreUri = null;
        blobFileName = null;
        blobMimeType = null;
    }


    // ============================================================
    // 文件选择器结果
    // ============================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (requestCode ==
                FILE_CHOOSER_REQUEST) {

            Uri[] results = null;


            if (resultCode ==
                    RESULT_OK &&
                    data != null) {


                if (data.getClipData() != null) {

                    int count =
                            data.getClipData()
                                    .getItemCount();


                    results =
                            new Uri[count];


                    for (
                            int i = 0;
                            i < count;
                            i++
                    ) {

                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }


                } else if (
                        data.getData() != null
                ) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }
            }


            if (filePathCallback != null) {

                filePathCallback
                        .onReceiveValue(
                                results
                        );

                filePathCallback = null;
            }
        }
    }


    // ============================================================
    // 存储权限
    // ============================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );


        if (requestCode ==
                WRITE_PERMISSION_REQUEST) {


            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                createPearlDownloadDirectory();


                Toast.makeText(
                        this,
                        "Pearl SR 下载目录已准备好",
                        Toast.LENGTH_SHORT
                ).show();


            } else {

                Toast.makeText(
                        this,
                        "未获得存储权限",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    // ============================================================
    // 返回
    // ============================================================

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }


    // ============================================================
    // 销毁
    // ============================================================

    @Override
    protected void onDestroy() {

        closeBlobOutput();

        if (webView != null) {

            webView.stopLoading();

            webView.loadUrl(
                    "about:blank"
            );

            webView.clearHistory();

            webView.removeAllViews();

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
                        }
