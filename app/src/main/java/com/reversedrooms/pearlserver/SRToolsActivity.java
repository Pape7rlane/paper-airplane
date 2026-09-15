package com.reversedrooms.pearlserver;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.Locale;

public class SRToolsActivity extends Activity {

    // ============================================================
    // SRTools
    // ============================================================

    private static final String SRTOOLS_URL =
            "https://srtools.neonteam.dev/";


    // ============================================================
    // 下载目录
    // ============================================================

    private static final String DOWNLOAD_FOLDER =
            "Pearl SR";


    // ============================================================
    // Request Code
    // ============================================================

    private static final int FILE_CHOOSER_REQUEST = 1001;

    private static final int WRITE_PERMISSION_REQUEST = 1002;


    // ============================================================
    // WebView
    // ============================================================

    private WebView webView;

    private ProgressBar progressBar;


    // ============================================================
    // 文件选择器
    // ============================================================

    private ValueCallback<Uri[]> filePathCallback;


    // ============================================================
    // Blob 下载
    // ============================================================

    private OutputStream blobOutputStream;

    private File blobTempFile;

    private Uri blobMediaStoreUri;

    private String blobFileName;

    private String blobMimeType;


    // ============================================================
    // Blob JS
    // ============================================================

    private boolean blobInterceptorInstalled = false;


    // ============================================================
    // 横竖屏按钮
    // ============================================================

    private TextView orientationButton;


    // ============================================================
    // Activity
    // ============================================================

    @Override
    protected void onCreate(Bundle state) {

        super.onCreate(state);


        // --------------------------------------------------------
        // 沉浸式全屏
        // --------------------------------------------------------

        enableFullscreen();


        // --------------------------------------------------------
        // 原布局
        // --------------------------------------------------------

        setContentView(
                R.layout.activity_srtools
        );


        webView =
                findViewById(
                        R.id.webView
                );


        progressBar =
                findViewById(
                        R.id.progressBar
                );


        // --------------------------------------------------------
        // 创建 Download/Pearl SR
        // --------------------------------------------------------

        createPearlDownloadDirectory();


        // --------------------------------------------------------
        // WebView
        // --------------------------------------------------------

        setupWebView();


        // --------------------------------------------------------
        // 刷新
        // --------------------------------------------------------

        View reloadButton =
                findViewById(
                        R.id.reloadButton
                );


        if (reloadButton != null) {

            reloadButton.setOnClickListener(
                    v -> {

                        blobInterceptorInstalled =
                                false;

                        webView.reload();
                    }
            );
        }


        // --------------------------------------------------------
        // 关闭
        // --------------------------------------------------------

        View closeButton =
                findViewById(
                        R.id.closeButton
                );


        if (closeButton != null) {

            closeButton.setOnClickListener(
                    v -> finish()
            );
        }


        // --------------------------------------------------------
        // 横竖屏切换
        // --------------------------------------------------------

        setupOrientationButton();


        // --------------------------------------------------------
        // 加载 SRTools
        // --------------------------------------------------------

        webView.loadUrl(
                SRTOOLS_URL
        );
    }


    // ============================================================
    // 沉浸式全屏
    // ============================================================

    private void enableFullscreen() {

        Window window =
                getWindow();


        // --------------------------------------------------------
        // 隐藏状态栏
        // --------------------------------------------------------

        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );


        // --------------------------------------------------------
        // 沉浸式系统 UI
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.KITKAT
        ) {

            window.getDecorView()
                    .setSystemUiVisibility(

                            View.SYSTEM_UI_FLAG_FULLSCREEN |

                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |

                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |

                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |

                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |

                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
        }
    }


    // ============================================================
    // 横竖屏切换按钮
    // ============================================================

    private void setupOrientationButton() {

        View rootView =
                findViewById(
                        android.R.id.content
                );


        if (!(rootView instanceof FrameLayout)) {

            return;
        }


        FrameLayout content =
                (FrameLayout) rootView;


        // --------------------------------------------------------
        // 创建按钮
        // --------------------------------------------------------

        orientationButton =
                new TextView(this);


        orientationButton.setText(
                "↻"
        );


        orientationButton.setTextSize(
                24
        );


        orientationButton.setTextColor(
                Color.WHITE
        );


        orientationButton.setGravity(
                Gravity.CENTER
        );


        orientationButton.setClickable(
                true
        );


        orientationButton.setFocusable(
                true
        );


        // --------------------------------------------------------
        // 圆形半透明背景
        // --------------------------------------------------------

        GradientDrawable background =
                new GradientDrawable();


        background.setShape(
                GradientDrawable.OVAL
        );


        background.setColor(
                0xCC202124
        );


        orientationButton.setBackground(
                background
        );


        // --------------------------------------------------------
        // 按钮大小
        // --------------------------------------------------------

        int size =
                dpToPx(52);


        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        size,
                        size
                );


        params.gravity =
                Gravity.END |
                Gravity.CENTER_VERTICAL;


        params.setMargins(
                0,
                0,
                dpToPx(14),
                0
        );


        content.addView(
                orientationButton,
                params
        );


        // --------------------------------------------------------
        // 点击
        // --------------------------------------------------------

        orientationButton.setOnClickListener(
                v -> toggleOrientation()
        );
    }


    // ============================================================
    // 横竖屏切换
    // ============================================================

    private void toggleOrientation() {

        int currentOrientation =
                getResources()
                        .getConfiguration()
                        .orientation;


        if (
                currentOrientation ==
                        Configuration.ORIENTATION_LANDSCAPE
        ) {

            // ----------------------------------------------------
            // 横屏 → 竖屏
            // ----------------------------------------------------

            setRequestedOrientation(
                    ActivityInfo
                            .SCREEN_ORIENTATION_PORTRAIT
            );


        } else {

            // ----------------------------------------------------
            // 竖屏 → 横屏
            // ----------------------------------------------------

            setRequestedOrientation(
                    ActivityInfo
                            .SCREEN_ORIENTATION_LANDSCAPE
            );
        }
    }


    // ============================================================
    // dp → px
    // ============================================================

    private int dpToPx(
            int dp
    ) {

        return Math.round(
                dp *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    // ============================================================
    // 横竖屏变化
    // ============================================================

    @Override
    public void onConfigurationChanged(
            Configuration newConfig
    ) {

        super.onConfigurationChanged(
                newConfig
        );


        // --------------------------------------------------------
        // 保持全屏
        // --------------------------------------------------------

        enableFullscreen();


        // --------------------------------------------------------
        // WebView 重新布局
        // --------------------------------------------------------

        if (
                webView != null
        ) {

            webView.requestLayout();


            webView.post(() -> {

                if (webView != null) {

                    webView.requestLayout();

                    injectZoomFix(
                            webView
                    );
                }
            });
        }
    }


    // ============================================================
    // WebView 设置
    // ============================================================

    private void setupWebView() {

        WebSettings settings =
                webView.getSettings();


        // --------------------------------------------------------
        // JavaScript
        // --------------------------------------------------------

        settings.setJavaScriptEnabled(
                true
        );


        settings.setDomStorageEnabled(
                true
        );


        settings.setDatabaseEnabled(
                true
        );


        settings.setLoadsImagesAutomatically(
                true
        );


        // --------------------------------------------------------
        // 页面行为
        // --------------------------------------------------------

        settings.setJavaScriptCanOpenWindowsAutomatically(
                true
        );


        settings.setSupportMultipleWindows(
                false
        );


        // ========================================================
        // 缩放
        // ========================================================

        settings.setSupportZoom(
                true
        );


        settings.setBuiltInZoomControls(
                true
        );


        // 隐藏旧式 + / - 按钮
        settings.setDisplayZoomControls(
                false
        );


        settings.setUseWideViewPort(
                true
        );


        settings.setLoadWithOverviewMode(
                false
        );


        // ========================================================
        // 混合内容
        // ========================================================

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.LOLLIPOP
        ) {

            settings.setMixedContentMode(
                    WebSettings
                            .MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }


        // ========================================================
        // WebViewClient
        // ========================================================

        webView.setWebViewClient(
                new WebViewClient() {

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


                        if (progressBar != null) {

                            progressBar.setVisibility(
                                    View.VISIBLE
                            );
                        }


                        // 尽早注入 Blob
                        injectBlobInterceptor(
                                view
                        );
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


                        if (progressBar != null) {

                            progressBar.setVisibility(
                                    View.GONE
                            );
                        }


                        // 强制网页允许缩放
                        injectZoomFix(
                                view
                        );


                        // 再次注入 Blob
                        injectBlobInterceptor(
                                view
                        );
                    }
                }
        );


        // ========================================================
        // WebChromeClient
        // ========================================================

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public void onProgressChanged(
                            WebView view,
                            int newProgress
                    ) {

                        super.onProgressChanged(
                                view,
                                newProgress
                        );


                        if (progressBar == null) {

                            return;
                        }


                        if (
                                newProgress >= 100
                        ) {

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
                    // 网页文件上传
                    // ====================================================

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams fileChooserParams
                    ) {

                        if (
                                SRToolsActivity.this
                                        .filePathCallback != null
                        ) {

                            SRToolsActivity.this
                                    .filePathCallback
                                    .onReceiveValue(
                                            null
                                    );
                        }


                        SRToolsActivity.this
                                .filePathCallback =
                                callback;


                        try {

                            Intent intent =
                                    fileChooserParams
                                            .createIntent();


                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER_REQUEST
                            );


                        } catch (Exception e) {

                            SRToolsActivity.this
                                    .filePathCallback =
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
                }
        );


        // ========================================================
        // 普通下载监听
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
    // 强制网页允许缩放
    // ============================================================

    private void injectZoomFix(
            WebView view
    ) {

        if (view == null) {

            return;
        }


        String javascript =
                "(function() {" +

                "try {" +

                "var metas =" +
                "document.getElementsByTagName('meta');" +

                "var viewport = null;" +

                "for (var i = 0;" +
                "i < metas.length;" +
                "i++) {" +

                "if (metas[i].name &&" +
                "metas[i].name.toLowerCase() ===" +
                "'viewport') {" +

                "viewport = metas[i];" +

                "break;" +

                "}" +

                "}" +

                "if (!viewport) {" +

                "viewport =" +
                "document.createElement('meta');" +

                "viewport.name = 'viewport';" +

                "if (document.head) {" +
                "document.head.appendChild(viewport);" +
                "}" +

                "}" +

                "if (viewport) {" +

                "viewport.setAttribute(" +
                "'content'," +

                "'width=device-width," +
                "initial-scale=1.0," +
                "minimum-scale=0.25," +
                "maximum-scale=5.0," +
                "user-scalable=yes'" +

                ");" +

                "}" +

                "} catch(e) {}" +

                "})();";


        view.evaluateJavascript(
                javascript,
                null
        );
    }


    // ============================================================
    // 创建 Pearl SR 下载目录
    // ============================================================

    private void createPearlDownloadDirectory() {

        // --------------------------------------------------------
        // Android 10+
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
        ) {

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
                        Environment.DIRECTORY_DOWNLOADS +
                                "/" +
                                DOWNLOAD_FOLDER
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


                if (uri != null) {

                    try {

                        OutputStream output =
                                getContentResolver()
                                        .openOutputStream(
                                                uri
                                        );


                        if (output != null) {

                            output.close();
                        }

                    } catch (Exception ignored) {
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
                }

            } catch (Exception ignored) {
            }


            return;
        }


        // --------------------------------------------------------
        // Android 9 及以下
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
        ) {

            if (
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
                    ) !=
                            PackageManager
                                    .PERMISSION_GRANTED
            ) {

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

                directory.mkdirs();
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
    // Android 9 及以下目录
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

        if (
                downloadUrl == null ||
                downloadUrl.trim().isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "下载地址为空",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        // --------------------------------------------------------
        // Blob
        // --------------------------------------------------------

        if (
                downloadUrl.startsWith("blob:")
        ) {

            String escapedUrl =
                    escapeJs(
                            downloadUrl
                    );


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


        // --------------------------------------------------------
        // HTTP / HTTPS
        // --------------------------------------------------------

        if (
                downloadUrl.startsWith("http://") ||
                downloadUrl.startsWith("https://")
        ) {

            downloadHttpFile(
                    downloadUrl,
                    userAgent,
                    contentDisposition,
                    mimeType
            );


            return;
        }


        // --------------------------------------------------------
        // 其他 URI
        // --------------------------------------------------------

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(downloadUrl)
                    );


            startActivity(
                    intent
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "无法处理下载地址",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // Blob JS 拦截器
    // ============================================================

    private void injectBlobInterceptor(
            WebView view
    ) {

        if (view == null) {

            return;
        }


        if (blobInterceptorInstalled) {

            return;
        }


        blobInterceptorInstalled = true;


        String javascript =
                "(function() {" +

                "if (window.__pearlBlobInstalled) return;" +

                "window.__pearlBlobInstalled = true;" +

                "window.__pearlBlobMap = new Map();" +

                "window.__pearlLastDownloadName = '';" +


                // =================================================
                // 点击下载链接时记录真实文件名
                // =================================================

                "document.addEventListener(" +
                "'click'," +

                "function(event) {" +

                "try {" +

                "let element =" +
                "event.target;" +

                "while (" +
                "element &&" +
                "element !== document" +
                ") {" +

                "if (" +
                "element.tagName === 'A'" +
                ") {" +

                "if (" +

                "element.href &&" +

                "element.href.indexOf('blob:') === 0" +

                ") {" +

                "if (" +
                "element.download" +
                ") {" +

                "window.__pearlLastDownloadName =" +
                "element.download;" +

                "}" +

                "}" +

                "break;" +

                "}" +

                "element =" +
                "element.parentElement;" +

                "}" +

                "} catch(e) {}" +

                "}," +

                "true" +

                ");" +


                // =================================================
                // createObjectURL
                // =================================================

                "const originalCreateObjectURL =" +
                "URL.createObjectURL;" +


                "URL.createObjectURL = function(blob) {" +

                "const url =" +
                "originalCreateObjectURL.call(URL, blob);" +


                "try {" +

                "window.__pearlBlobMap.set(" +
                "url," +
                "blob" +
                ");" +

                "} catch(e) {}" +


                "return url;" +

                "};" +


                // =================================================
                // revokeObjectURL
                // =================================================

                "const originalRevokeObjectURL =" +
                "URL.revokeObjectURL;" +


                "URL.revokeObjectURL = function(url) {" +

                "try {" +

                "setTimeout(function() {" +

                "try {" +

                "window.__pearlBlobMap.delete(url);" +

                "} catch(e) {}" +

                "}, 30000);" +

                "} catch(e) {}" +


                "try {" +

                "return originalRevokeObjectURL.call(" +
                "URL," +
                "url" +
                ");" +

                "} catch(e) {}" +

                "};" +


                // =================================================
                // Blob 下载
                // =================================================

                "window.__pearlDownloadBlob =" +
                "async function(url, mimeType) {" +

                "try {" +

                "let blob =" +
                "window.__pearlBlobMap.get(url);" +


                // -------------------------------------------------
                // 找不到 Blob 时尝试 fetch
                // -------------------------------------------------

                "if (!blob) {" +

                "try {" +

                "const response =" +
                "await fetch(url);" +

                "if (response.ok) {" +

                "blob =" +
                "await response.blob();" +

                "}" +

                "} catch(e) {}" +

                "}" +


                "if (!blob) {" +

                "throw new Error(" +
                "'找不到 Blob 对象'" +
                ");" +

                "}" +


                // =================================================
                // MIME
                // =================================================

                "const finalMime =" +

                "mimeType ||" +

                "blob.type ||" +

                "'application/octet-stream';" +


                // =================================================
                // 文件名
                // =================================================

                "let fileName =" +
                "window.__pearlLastDownloadName || '';" +


                // -------------------------------------------------
                // 如果没有记录，再检查精确 Blob 链接
                // -------------------------------------------------

                "if (!fileName) {" +

                "try {" +

                "const links =" +
                "document.querySelectorAll('a');" +


                "for (" +
                "let i = 0;" +
                "i < links.length;" +
                "i++" +
                ") {" +

                "const a = links[i];" +


                "if (" +

                "a.href === url &&" +

                "a.download" +

                ") {" +

                "fileName =" +
                "a.download;" +

                "break;" +

                "}" +

                "}" +

                "} catch(e) {}" +

                "}" +


                // -------------------------------------------------
                // a[download] 备用
                // -------------------------------------------------

                "if (!fileName) {" +

                "try {" +

                "const links =" +
                "document.querySelectorAll(" +
                "'a[download]'" +
                ");" +


                "for (" +
                "let i = 0;" +
                "i < links.length;" +
                "i++" +
                ") {" +

                "const a = links[i];" +


                "if (a.download) {" +

                "fileName =" +
                "a.download;" +

                "break;" +

                "}" +

                "}" +

                "} catch(e) {}" +

                "}" +


                // =================================================
                // 最终兜底
                // =================================================

                "if (" +

                "!fileName ||" +

                "fileName === 'null' ||" +

                "fileName === 'undefined'" +

                ") {" +

                "fileName =" +
                "'download.bin';" +

                "}" +


                // =================================================
                // 开始下载
                // =================================================

                "window.PearlDownload.beginBlob(" +

                "fileName," +

                "finalMime," +

                "blob.size" +

                ");" +


                // =================================================
                // 分块
                // =================================================

                "const chunkSize =" +
                "256 * 1024;" +


                "let offset = 0;" +


                "while (" +
                "offset < blob.size" +
                ") {" +


                "const chunk =" +

                "blob.slice(" +

                "offset," +

                "Math.min(" +

                "offset + chunkSize," +

                "blob.size" +

                ")" +

                ");" +


                "const buffer =" +
                "await chunk.arrayBuffer();" +


                "const bytes =" +
                "new Uint8Array(buffer);" +


                "let binary = '';" +


                "const blockSize = 8192;" +


                "for (" +

                "let i = 0;" +

                "i < bytes.length;" +

                "i += blockSize" +

                ") {" +


                "const sub =" +

                "bytes.subarray(" +

                "i," +

                "Math.min(" +

                "i + blockSize," +

                "bytes.length" +

                ")" +

                ");" +


                "binary +=" +

                "String.fromCharCode.apply(" +

                "null," +

                "sub" +

                ");" +

                "}" +


                "const base64 =" +
                "btoa(binary);" +


                "window.PearlDownload" +
                ".receiveBlobChunk(base64);" +


                "offset +=" +
                "bytes.length;" +


                "}" +


                // =================================================
                // 完成
                // =================================================

                "window.PearlDownload.finishBlob();" +


                "} catch(e) {" +

                "window.PearlDownload.error(" +

                "String(e)" +

                ");" +

                "}" +


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


        // ========================================================
        // 开始 Blob
        // ========================================================

        @JavascriptInterface
        public synchronized void beginBlob(
                String fileName,
                String mimeType,
                long size
        ) {

            try {

                cleanupBlobDownload();


                // ------------------------------------------------
                // 文件名
                // ------------------------------------------------

                blobFileName =
                        normalizeSrToolsFileName(
                                sanitizeFileName(
                                        fileName
                                )
                        );


                if (
                        blobFileName == null ||
                        blobFileName.isEmpty()
                ) {

                    blobFileName =
                            "download.bin";
                }


                // ------------------------------------------------
                // MIME
                // ------------------------------------------------

                blobMimeType =
                        mimeType;


                if (
                        blobMimeType == null ||
                        blobMimeType.trim().isEmpty()
                ) {

                    blobMimeType =
                            getMimeType(
                                    blobFileName
                            );
                }


                // =================================================
                // Android 10+
                // =================================================

                if (
                        Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q
                ) {

                    ContentValues values =
                            new ContentValues();


                    values.put(
                            MediaStore.Downloads
                                    .DISPLAY_NAME,
                            blobFileName
                    );


                    values.put(
                            MediaStore.Downloads
                                    .MIME_TYPE,
                            blobMimeType
                    );


                    values.put(
                            MediaStore.Downloads
                                    .RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS +
                                    "/" +
                                    DOWNLOAD_FOLDER
                    );


                    values.put(
                            MediaStore.Downloads
                                    .IS_PENDING,
                            1
                    );


                    blobMediaStoreUri =
                            getContentResolver().insert(
                                    MediaStore.Downloads
                                            .EXTERNAL_CONTENT_URI,
                                    values
                            );


                    if (
                            blobMediaStoreUri == null
                    ) {

                        throw new IOException(
                                "无法创建下载文件"
                        );
                    }


                    // ★ 同步打开
                    blobOutputStream =
                            getContentResolver()
                                    .openOutputStream(
                                            blobMediaStoreUri
                                    );


                    if (
                            blobOutputStream == null
                    ) {

                        throw new IOException(
                                "下载文件没有打开"
                        );
                    }


                } else {

                    // =================================================
                    // Android 9-
                    // =================================================

                    if (
                            Build.VERSION.SDK_INT >=
                                    Build.VERSION_CODES.M
                    ) {

                        if (
                                ContextCompat
                                        .checkSelfPermission(
                                                SRToolsActivity.this,
                                                Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE
                                        ) !=
                                        PackageManager
                                                .PERMISSION_GRANTED
                        ) {

                            throw new IOException(
                                    "需要存储权限"
                            );
                        }
                    }


                    File directory =
                            getLegacyDownloadDirectory();


                    if (!directory.exists()) {

                        if (
                                !directory.mkdirs() &&
                                !directory.exists()
                        ) {

                            throw new IOException(
                                    "无法创建下载目录"
                            );
                        }
                    }


                    File outputFile =
                            getUniqueFile(
                                    directory,
                                    blobFileName
                            );


                    blobTempFile =
                            outputFile;


                    blobFileName =
                            outputFile.getName();


                    // ★ 同步打开
                    blobOutputStream =
                            new FileOutputStream(
                                    outputFile
                            );
                }


                android.util.Log.d(
                        "SRToolsActivity",
                        "Blob 开始下载: " +
                                blobFileName +
                                " (" +
                                size +
                                " bytes)"
                );


            } catch (Exception e) {

                cleanupBlobDownload();


                final String error =
                        e.getMessage() == null
                                ? e.toString()
                                : e.getMessage();


                runOnUiThread(() -> {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "开始下载失败：" +
                                    error,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }


        // ========================================================
        // Blob Chunk
        // ========================================================

        @JavascriptInterface
        public synchronized void receiveBlobChunk(
                String base64
        ) {

            try {

                if (
                        blobOutputStream == null
                ) {

                    throw new IOException(
                            "下载文件没有打开"
                    );
                }


                if (
                        base64 == null ||
                        base64.isEmpty()
                ) {

                    return;
                }


                byte[] data =
                        Base64.getDecoder()
                                .decode(
                                        base64
                                );


                blobOutputStream.write(
                        data
                );


            } catch (Exception e) {

                final String error =
                        e.getMessage() == null
                                ? e.toString()
                                : e.getMessage();


                cleanupBlobDownload();


                runOnUiThread(() -> {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "写入下载文件失败：" +
                                    error,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }


        // ========================================================
        // Blob Finish
        // ========================================================

        @JavascriptInterface
        public synchronized void finishBlob() {

            try {

                // ------------------------------------------------
                // 关闭文件
                // ------------------------------------------------

                if (
                        blobOutputStream != null
                ) {

                    blobOutputStream.flush();

                    blobOutputStream.close();

                    blobOutputStream = null;
                }


                // ------------------------------------------------
                // Android 10+
                // ------------------------------------------------

                if (
                        Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q
                ) {

                    if (
                            blobMediaStoreUri != null
                    ) {

                        ContentValues values =
                                new ContentValues();


                        values.put(
                                MediaStore.Downloads
                                        .IS_PENDING,
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


                final String completedFileName =
                        blobFileName;


                android.util.Log.d(
                        "SRToolsActivity",
                        "Blob 下载完成: " +
                                completedFileName
                );


                // ------------------------------------------------
                // 清理状态
                // ------------------------------------------------

                blobTempFile = null;

                blobMediaStoreUri = null;

                blobFileName = null;

                blobMimeType = null;


                // ------------------------------------------------
                // Toast
                // ------------------------------------------------

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

                cleanupBlobDownload();


                final String error =
                        e.getMessage() == null
                                ? e.toString()
                                : e.getMessage();


                runOnUiThread(() -> {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "完成下载失败：" +
                                    error,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }


        // ========================================================
        // Blob Error
        // ========================================================

        @JavascriptInterface
        public synchronized void error(
                String message
        ) {

            cleanupBlobDownload();


            final String error =
                    message == null ||
                            message.isEmpty()
                            ? "未知错误"
                            : message;


            runOnUiThread(() -> {

                Toast.makeText(
                        SRToolsActivity.this,
                        "Blob 下载失败：" +
                                error,
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

            HttpURLConnection connection =
                    null;


            try {

                URL url =
                        new URL(
                                downloadUrl
                        );


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


                if (
                        userAgent != null &&
                        !userAgent.isEmpty()
                ) {

                    connection.setRequestProperty(
                            "User-Agent",
                            userAgent
                    );
                }


                int responseCode =
                        connection.getResponseCode();


                if (
                        responseCode < 200 ||
                        responseCode >= 300
                ) {

                    throw new IOException(
                            "HTTP " +
                                    responseCode
                    );
                }


                // =================================================
                // 文件名
                // =================================================

                String fileName =
                        resolveDownloadFileName(
                                downloadUrl,
                                contentDisposition
                        );


                fileName =
                        normalizeSrToolsFileName(
                                fileName
                        );


                // =================================================
                // MIME
                // =================================================

                String finalMimeType =
                        mimeType;


                if (
                        finalMimeType == null ||
                        finalMimeType.isEmpty()
                ) {

                    finalMimeType =
                            getMimeType(
                                    fileName
                            );
                }


                // =================================================
                // Android 10+
                // =================================================

                if (
                        Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q
                ) {

                    ContentValues values =
                            new ContentValues();


                    values.put(
                            MediaStore.Downloads
                                    .DISPLAY_NAME,
                            fileName
                    );


                    values.put(
                            MediaStore.Downloads
                                    .MIME_TYPE,
                            finalMimeType
                    );


                    values.put(
                            MediaStore.Downloads
                                    .RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS +
                                    "/" +
                                    DOWNLOAD_FOLDER
                    );


                    values.put(
                            MediaStore.Downloads
                                    .IS_PENDING,
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
                                    connection
                                            .getInputStream();

                            OutputStream output =
                                    getContentResolver()
                                            .openOutputStream(
                                                    uri
                                            )
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
                            MediaStore.Downloads
                                    .IS_PENDING,
                            0
                    );


                    getContentResolver().update(
                            uri,
                            completed,
                            null,
                            null
                    );


                } else {

                    // =================================================
                    // Android 9-
                    // =================================================

                    if (
                            Build.VERSION.SDK_INT >=
                                    Build.VERSION_CODES.M
                    ) {

                        if (
                                ContextCompat
                                        .checkSelfPermission(
                                                SRToolsActivity.this,
                                                Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE
                                        ) !=
                                        PackageManager
                                                .PERMISSION_GRANTED
                        ) {

                            runOnUiThread(() ->
                                    requestPermissions(
                                            new String[]{
                                                    Manifest.permission
                                                            .WRITE_EXTERNAL_STORAGE
                                            },
                                            WRITE_PERMISSION_REQUEST
                                    )
                            );


                            throw new IOException(
                                    "需要存储权限"
                            );
                        }
                    }


                    File directory =
                            getLegacyDownloadDirectory();


                    if (!directory.exists()) {

                        if (
                                !directory.mkdirs() &&
                                !directory.exists()
                        ) {

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


                    fileName =
                            outputFile.getName();


                    try (
                            InputStream input =
                                    connection
                                            .getInputStream();

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
    // 文件名解析
    // ============================================================

    private String resolveDownloadFileName(
            String downloadUrl,
            String contentDisposition
    ) {

        // --------------------------------------------------------
        // 1. Content-Disposition
        // --------------------------------------------------------

        String fileName =
                extractFileName(
                        contentDisposition
                );


        if (
                isValidFileName(
                        fileName
                )
        ) {

            return sanitizeFileName(
                    fileName
            );
        }


        // --------------------------------------------------------
        // 2. URL
        // --------------------------------------------------------

        fileName =
                extractFileNameFromUrl(
                        downloadUrl
                );


        if (
                isValidFileName(
                        fileName
                )
        ) {

            try {

                fileName =
                        URLDecoder.decode(
                                fileName,
                                "UTF-8"
                        );

            } catch (Exception ignored) {
            }


            return sanitizeFileName(
                    fileName
            );
        }


        // --------------------------------------------------------
        // 3. URL 中搜索 SRTools 文件名
        // --------------------------------------------------------

        if (
                downloadUrl != null
        ) {

            String lowerUrl =
                    downloadUrl.toLowerCase(
                            Locale.ROOT
                    );


            if (
                    lowerUrl.contains(
                            "freesr-data.json"
                    )
            ) {

                return "freesr-data.json";
            }


            if (
                    lowerUrl.contains(
                            "config.json"
                    )
            ) {

                return "config.json";
            }
        }


        // --------------------------------------------------------
        // 4. 兜底
        // --------------------------------------------------------

        return "download.bin";
    }


    // ============================================================
    // SRTools 文件名规范化
    // ============================================================

    private String normalizeSrToolsFileName(
            String fileName
    ) {

        if (
                fileName == null
        ) {

            return "download.bin";
        }


        String value =
                fileName.trim();


        if (
                value.isEmpty() ||
                value.equalsIgnoreCase("null") ||
                value.equalsIgnoreCase("undefined")
        ) {

            return "download.bin";
        }


        if (
                value.equalsIgnoreCase(
                        "freesr-data.json"
                )
        ) {

            return "freesr-data.json";
        }


        if (
                value.equalsIgnoreCase(
                        "config.json"
                )
        ) {

            return "config.json";
        }


        return sanitizeFileName(
                value
        );
    }


    // ============================================================
    // 文件名是否有效
    // ============================================================

    private boolean isValidFileName(
            String fileName
    ) {

        if (
                fileName == null
        ) {

            return false;
        }


        String value =
                fileName.trim();


        return !value.isEmpty() &&
                !value.equalsIgnoreCase("null") &&
                !value.equalsIgnoreCase("undefined");
    }


    // ============================================================
    // Content-Disposition
    // ============================================================

    private String extractFileName(
            String contentDisposition
    ) {

        if (
                contentDisposition == null
        ) {

            return null;
        }


        try {

            String lower =
                    contentDisposition.toLowerCase(
                            Locale.ROOT
                    );


            // ----------------------------------------------------
            // filename*=UTF-8''
            // ----------------------------------------------------

            int encodedIndex =
                    lower.indexOf(
                            "filename*="
                    );


            if (
                    encodedIndex >= 0
            ) {

                String value =
                        contentDisposition.substring(
                                encodedIndex +
                                        "filename*=".length()
                        ).trim();


                int semicolon =
                        value.indexOf(';');


                if (
                        semicolon >= 0
                ) {

                    value =
                            value.substring(
                                    0,
                                    semicolon
                            );
                }


                if (
                        value.startsWith(
                                "UTF-8''"
                        ) ||
                        value.startsWith(
                                "utf-8''"
                        )
                ) {

                    value =
                            value.substring(
                                    7
                            );
                }


                try {

                    value =
                            URLDecoder.decode(
                                    value,
                                    "UTF-8"
                            );

                } catch (Exception ignored) {
                }


                return value.trim();
            }


            // ----------------------------------------------------
            // filename=
            // ----------------------------------------------------

            int index =
                    lower.indexOf(
                            "filename="
                    );


            if (
                    index >= 0
            ) {

                String value =
                        contentDisposition.substring(
                                index +
                                        "filename=".length()
                        ).trim();


                if (
                        value.startsWith("\"")
                ) {

                    int end =
                            value.indexOf(
                                    "\"",
                                    1
                            );


                    if (
                            end > 1
                    ) {

                        return value.substring(
                                1,
                                end
                        );
                    }
                }


                int semicolon =
                        value.indexOf(';');


                if (
                        semicolon >= 0
                ) {

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
            String downloadUrl
    ) {

        if (
                downloadUrl == null
        ) {

            return null;
        }


        try {

            Uri uri =
                    Uri.parse(
                            downloadUrl
                    );


            String path =
                    uri.getPath();


            if (
                    path == null ||
                    path.isEmpty()
            ) {

                return null;
            }


            int slash =
                    path.lastIndexOf('/');


            if (
                    slash >= 0 &&
                    slash + 1 < path.length()
            ) {

                return path.substring(
                        slash + 1
                );
            }

        } catch (Exception ignored) {
        }


        return null;
    }


    // ============================================================
    // 清理文件名
    // ============================================================

    private String sanitizeFileName(
            String fileName
    ) {

        if (
                fileName == null
        ) {

            return "download.bin";
        }


        String result =
                fileName.trim();


        if (
                result.isEmpty()
        ) {

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
    // 获取不重复文件
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


        if (
                !file.exists()
        ) {

            return file;
        }


        String name =
                fileName;


        String extension =
                "";


        int dot =
                fileName.lastIndexOf('.');


        if (
                dot > 0
        ) {

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


        while (
                file.exists()
        ) {

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
    // MIME
    // ============================================================

    private String getMimeType(
            String fileName
    ) {

        if (
                fileName == null
        ) {

            return "application/octet-stream";
        }


        String lower =
                fileName.toLowerCase(
                        Locale.ROOT
                );


        if (
                lower.endsWith(".json")
        ) {

            return "application/json";
        }


        if (
                lower.endsWith(".zip")
        ) {

            return "application/zip";
        }


        if (
                lower.endsWith(".txt")
        ) {

            return "text/plain";
        }


        if (
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg")
        ) {

            return "image/jpeg";
        }


        if (
                lower.endsWith(".png")
        ) {

            return "image/png";
        }


        return "application/octet-stream";
    }


    // ============================================================
    // Stream
    // ============================================================

    private void copyStream(
            InputStream input,
            OutputStream output
    ) throws IOException {

        byte[] buffer =
                new byte[64 * 1024];


        int length;


        while (
                (length =
                        input.read(buffer)) != -1
        ) {

            output.write(
                    buffer,
                    0,
                    length
            );
        }


        output.flush();
    }


    // ============================================================
    // JS Escape
    // ============================================================

    private String escapeJs(
            String value
    ) {

        if (
                value == null
        ) {

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
    // Blob Output
    // ============================================================

    private synchronized void closeBlobOutput() {

        if (
                blobOutputStream != null
        ) {

            try {

                blobOutputStream.close();

            } catch (Exception ignored) {
            }


            blobOutputStream = null;
        }
    }


    // ============================================================
    // Blob Cleanup
    // ============================================================

    private synchronized void cleanupBlobDownload() {

        closeBlobOutput();


        // --------------------------------------------------------
        // MediaStore
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
        ) {

            if (
                    blobMediaStoreUri != null
            ) {

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


        // --------------------------------------------------------
        // Legacy
        // --------------------------------------------------------

        if (
                blobTempFile != null
        ) {

            try {

                if (
                        blobTempFile.exists()
                ) {

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
    // 文件选择器
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


        if (
                requestCode ==
                        FILE_CHOOSER_REQUEST
        ) {

            Uri[] results = null;


            if (
                    resultCode == RESULT_OK &&
                    data != null
            ) {

                // ------------------------------------------------
                // 多文件
                // ------------------------------------------------

                if (
                        data.getClipData() != null
                ) {

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


                // ------------------------------------------------
                // 单文件
                // ------------------------------------------------

                } else if (
                        data.getData() != null
                ) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }
            }


            if (
                    filePathCallback != null
            ) {

                filePathCallback
                        .onReceiveValue(
                                results
                        );


                filePathCallback = null;
            }
        }
    }


    // ============================================================
    // 权限
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


        if (
                requestCode ==
                        WRITE_PERMISSION_REQUEST
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager
                                    .PERMISSION_GRANTED
            ) {

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

        if (
                webView != null &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }


    // ============================================================
    // Destroy
    // ============================================================

    @Override
    protected void onDestroy() {

        cleanupBlobDownload();


        if (
                webView != null
        ) {

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