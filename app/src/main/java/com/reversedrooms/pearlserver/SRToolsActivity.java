package com.reversedrooms.pearlserver;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityNotFoundException;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

public class SRToolsActivity extends Activity {

    // ============================================================
    // SRTools
    // ============================================================

    private static final String SRTOOLS_URL =
            "https://srtools.neonteam.dev/";

    private static final String DOWNLOAD_FOLDER =
            "Pearl SR";

    private static final int FILE_CHOOSER_REQUEST = 1001;

    private static final int WRITE_REQUEST = 1002;

    /*
     * 浏览器式桌面网页宽度。
     *
     * 不使用 width=device-width，
     * 让横屏/竖屏都保持桌面网页缩放效果。
     */
    private static final int WEB_PAGE_WIDTH = 1280;

    // ============================================================
    // Views
    // ============================================================

    private WebView webView;

    private ProgressBar progressBar;

    private Button reloadButton;

    private Button closeButton;

    private Button orientationButton;

    // ============================================================
    // File chooser
    // ============================================================

    private ValueCallback<Uri[]> filePathCallback;

    // ============================================================
    // Blob
    // ============================================================

    private OutputStream blobOutputStream;

    private File blobTempFile;

    private Uri blobMediaStoreUri;

    private String blobFileName;

    private String blobMimeType;

    private long blobExpectedSize = -1L;

    private long blobWrittenSize = 0L;

    // ============================================================
    // Blob Document Start Script
    // ============================================================

    /*
     * 重要：
     *
     * 这个脚本不是在 onPageFinished 才注入。
     *
     * 它会通过 AndroidX WebKit 的
     * addDocumentStartJavaScript()
     *
     * 在网页自己的 JS 执行之前安装。
     *
     * 这样 SRTools 创建 Blob URL 时，
     * 我们就能够第一时间保存 Blob 对象。
     */
    private static final String DOCUMENT_START_BLOB_SCRIPT =

            "(function(){"

                    // ------------------------------------------------
                    // 防止重复安装
                    // ------------------------------------------------

                    + "if(window.__pearlBlobHookInstalled){return;}"
                    + "window.__pearlBlobHookInstalled=true;"

                    // ------------------------------------------------
                    // Blob Map
                    // ------------------------------------------------

                    + "window.__pearlBlobMap=new Map();"

                    // ------------------------------------------------
                    // 下载文件名
                    // ------------------------------------------------

                    + "window.__pearlLastDownloadName='';"

                    // ------------------------------------------------
                    // 原始 API
                    // ------------------------------------------------

                    + "var __pearlOriginalCreateObjectURL="
                    + "URL.createObjectURL.bind(URL);"

                    + "var __pearlOriginalRevokeObjectURL="
                    + "URL.revokeObjectURL.bind(URL);"

                    // ------------------------------------------------
                    // Hook createObjectURL
                    // ------------------------------------------------

                    + "URL.createObjectURL=function(object){"

                    + "try{"

                    + "var url="
                    + "__pearlOriginalCreateObjectURL(object);"

                    + "try{"
                    + "window.__pearlBlobMap.set(url,object);"
                    + "}catch(e){}"

                    + "return url;"

                    + "}catch(e){"

                    + "return __pearlOriginalCreateObjectURL(object);"

                    + "}"

                    + "};"

                    // ------------------------------------------------
                    // Hook revokeObjectURL
                    // ------------------------------------------------

                    + "URL.revokeObjectURL=function(url){"

                    + "try{"
                    + "window.__pearlBlobMap.delete(url);"
                    + "}catch(e){}"

                    + "try{"
                    + "return __pearlOriginalRevokeObjectURL(url);"
                    + "}catch(e){"

                    + "return undefined;"

                    + "}"

                    + "};"

                    // ------------------------------------------------
                    // 获取文件名
                    // ------------------------------------------------

                    + "window.__pearlGetDownloadName=function(){"

                    + "try{"

                    + "if(window.__pearlLastDownloadName){"
                    + "return window.__pearlLastDownloadName;"
                    + "}"

                    + "var links="
                    + "document.querySelectorAll('a[download]');"

                    + "for(var i=0;i<links.length;i++){"

                    + "if(links[i].download){"
                    + "return links[i].download;"
                    + "}"

                    + "}"

                    + "}catch(e){}"

                    + "return 'download.bin';"

                    + "};"

                    // ------------------------------------------------
                    // 监听点击
                    // ------------------------------------------------

                    + "document.addEventListener("
                    + "'click',"
                    + "function(event){"

                    + "try{"

                    + "var element=event.target;"

                    + "while(element && "
                    + "element!==document){"

                    + "if(element.tagName==='A'){"

                    + "if(element.href && "
                    + "element.href.indexOf('blob:')===0){"

                    + "if(element.download){"

                    + "window.__pearlLastDownloadName="
                    + "element.download;"

                    + "}"

                    /*
                     * 不在这里直接强行处理。
                     *
                     * 让网站正常触发 click，
                     * DownloadListener 再接管 blob URL。
                     */
                    + "}"

                    + "break;"

                    + "}"

                    + "element=element.parentElement;"

                    + "}"

                    + "}catch(e){}"

                    + "},true);"

                    // ------------------------------------------------
                    // 下载 Blob
                    // ------------------------------------------------

                    + "window.__pearlDownloadBlob="
                    + "function(url,mimeType){"

                    + "try{"

                    + "var blob="
                    + "window.__pearlBlobMap.get(url);"

                    // ------------------------------------------------
                    // 找不到 Blob
                    // ------------------------------------------------

                    + "if(!blob){"

                    + "if(window.AndroidBlob && "
                    + "window.AndroidBlob.abortBlob){"

                    + "window.AndroidBlob.abortBlob("
                    + "'找不到 Blob 对象: '+url"
                    + ");"

                    + "}"

                    + "return false;"

                    + "}"

                    // ------------------------------------------------
                    // 文件名
                    // ------------------------------------------------

                    + "var fileName="
                    + "window.__pearlLastDownloadName||'';"

                    + "if(!fileName){"

                    + "fileName="
                    + "window.__pearlGetDownloadName();"

                    + "}"

                    + "if(!fileName){"
                    + "fileName='download.bin';"
                    + "}"

                    // ------------------------------------------------
                    // 开始 Java 下载
                    // ------------------------------------------------

                    + "if(!window.AndroidBlob || "
                    + "!window.AndroidBlob.beginBlob){"

                    + "return false;"

                    + "}"

                    + "window.AndroidBlob.beginBlob("
                    + "fileName,"
                    + "mimeType||blob.type||"
                    + "'application/octet-stream',"
                    + "blob.size"
                    + ");"

                    // ------------------------------------------------
                    // Blob Stream
                    // ------------------------------------------------

                    + "if(blob.stream){"

                    + "var reader="
                    + "blob.stream().getReader();"

                    + "var readNext=function(){"

                    + "reader.read().then(function(result){"

                    + "if(result.done){"

                    + "window.AndroidBlob.finishBlob();"

                    + "return;"

                    + "}"

                    + "var bytes=result.value;"

                    + "var CHUNK=32768;"

                    + "for(var start=0;"
                    + "start<bytes.length;"
                    + "start+=CHUNK){"

                    + "var part="
                    + "bytes.subarray("
                    + "start,"
                    + "Math.min(start+CHUNK,bytes.length)"
                    + ");"

                    + "var binary='';"

                    + "for(var i=0;"
                    + "i<part.length;"
                    + "i++){"

                    + "binary+="
                    + "String.fromCharCode(part[i]);"

                    + "}"

                    + "window.AndroidBlob.receiveBlobChunk("
                    + "btoa(binary)"
                    + ");"

                    + "}"

                    + "readNext();"

                    + "}).catch(function(error){"

                    + "window.AndroidBlob.abortBlob("
                    + "String(error)"
                    + ");"

                    + "});"

                    + "};"

                    + "readNext();"

                    + "}else{"

                    // ------------------------------------------------
                    // 老 WebView fallback
                    // ------------------------------------------------

                    + "var reader="
                    + "new FileReader();"

                    + "reader.onloadend=function(){"

                    + "try{"

                    + "var result="
                    + "reader.result;"

                    + "var base64="
                    + "result.split(',')[1];"

                    + "var STEP=65536;"

                    + "for(var p=0;"
                    + "p<base64.length;"
                    + "p+=STEP){"

                    + "window.AndroidBlob.receiveBase64Chunk("
                    + "base64.substring(p,p+STEP)"
                    + ");"

                    + "}"

                    + "window.AndroidBlob.finishBlob();"

                    + "}catch(error){"

                    + "window.AndroidBlob.abortBlob("
                    + "String(error)"
                    + ");"

                    + "}"

                    + "};"

                    + "reader.onerror=function(){"

                    + "window.AndroidBlob.abortBlob("
                    + "'FileReader error'"
                    + ");"

                    + "};"

                    + "reader.readAsDataURL(blob);"

                    + "}"

                    + "return true;"

                    + "}catch(error){"

                    + "if(window.AndroidBlob && "
                    + "window.AndroidBlob.abortBlob){"

                    + "window.AndroidBlob.abortBlob("
                    + "String(error)"
                    + ");"

                    + "}"

                    + "return false;"

                    + "}"

                    + "};"

                    + "})();";

    // ============================================================
    // Activity
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

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

        reloadButton =
                findViewById(
                        R.id.reloadButton
                );

        closeButton =
                findViewById(
                        R.id.closeButton
                );

        orientationButton =
                findViewById(
                        R.id.orientationButton
                );

        // --------------------------------------------------------
        // WebView 布局
        // --------------------------------------------------------

        if (webView != null) {

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    );

            params.leftMargin = 0;
            params.topMargin = 0;
            params.rightMargin = 0;
            params.bottomMargin = 0;

            webView.setLayoutParams(params);
        }

        // --------------------------------------------------------
        // 系统栏
        // --------------------------------------------------------

        applyFullscreenForOrientation();

        // --------------------------------------------------------
        // WebView
        // --------------------------------------------------------

        setupWebView();

        // --------------------------------------------------------
        // 按钮
        // --------------------------------------------------------

        setupButtons();

        // --------------------------------------------------------
        // Android 9 及以下
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT
                        <= Build.VERSION_CODES.P
        ) {

            if (
                    checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                            != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        WRITE_REQUEST
                );
            }
        }

        // --------------------------------------------------------
        // 加载 SRTools
        // --------------------------------------------------------

        if (webView != null) {

            webView.loadUrl(
                    SRTOOLS_URL
            );
        }
    }

    // ============================================================
    // Buttons
    // ============================================================

    private void setupButtons() {

        // --------------------------------------------------------
        // Reload
        // --------------------------------------------------------

        if (reloadButton != null) {

            reloadButton.setOnClickListener(
                    v -> {

                        if (webView != null) {
                            webView.reload();
                        }
                    }
            );
        }

        // --------------------------------------------------------
        // Close
        // --------------------------------------------------------

        if (closeButton != null) {

            closeButton.setOnClickListener(
                    v -> finish()
            );
        }

        // --------------------------------------------------------
        // Orientation
        // --------------------------------------------------------

        if (orientationButton != null) {

            orientationButton.setOnClickListener(
                    v -> {

                        boolean landscape =
                                getResources()
                                        .getConfiguration()
                                        .orientation
                                        == Configuration.ORIENTATION_LANDSCAPE;

                        if (landscape) {

                            setRequestedOrientation(
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            );

                        } else {

                            setRequestedOrientation(
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            );
                        }
                    }
            );
        }

        updateOrientationButton();
    }

    // ============================================================
    // Fullscreen
    // ============================================================

    private void applyFullscreenForOrientation() {

        Window window =
                getWindow();

        // --------------------------------------------------------
        // Android 11+
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.R
        ) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }

        } else {

            // ----------------------------------------------------
            // Android 10-
            // ----------------------------------------------------

            window.getDecorView()
                    .setSystemUiVisibility(

                            View.SYSTEM_UI_FLAG_FULLSCREEN

                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
        }

        updateOrientationButton();
    }

    // ============================================================
    // Orientation Button
    // ============================================================

    private void updateOrientationButton() {

        if (orientationButton == null) {
            return;
        }

        boolean landscape =
                getResources()
                        .getConfiguration()
                        .orientation
                        == Configuration.ORIENTATION_LANDSCAPE;

        if (landscape) {

            orientationButton.setText("竖");

            orientationButton.setContentDescription(
                    "切换到竖屏"
            );

        } else {

            orientationButton.setText("横");

            orientationButton.setContentDescription(
                    "切换到横屏"
            );
        }
    }

    // ============================================================
    // Orientation Changed
    // ============================================================

    @Override
    public void onConfigurationChanged(
            Configuration newConfig
    ) {

        super.onConfigurationChanged(newConfig);

        applyFullscreenForOrientation();

        updateOrientationButton();

        if (webView != null) {

            webView.postDelayed(
                    () -> {

                        try {

                            webView.evaluateJavascript(
                                    "window.dispatchEvent("
                                            + "new Event('resize')"
                                            + ");",
                                    null
                            );

                            webView.requestLayout();

                        } catch (Exception ignored) {
                        }
                    },
                    200
            );
        }
    }

    // ============================================================
    // WebView
    // ============================================================

    private void setupWebView() {

        if (webView == null) {
            return;
        }

        WebSettings settings =
                webView.getSettings();

        // --------------------------------------------------------
        // JavaScript
        // --------------------------------------------------------

        settings.setJavaScriptEnabled(true);

        // --------------------------------------------------------
        // DOM / Storage
        // --------------------------------------------------------

        settings.setDomStorageEnabled(true);

        settings.setDatabaseEnabled(true);

        // --------------------------------------------------------
        // Zoom
        // --------------------------------------------------------

        settings.setSupportZoom(true);

        settings.setBuiltInZoomControls(true);

        settings.setDisplayZoomControls(false);

        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(true);

        settings.setTextZoom(100);

        // --------------------------------------------------------
        // Content
        // --------------------------------------------------------

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(
                true
        );

        settings.setSupportMultipleWindows(false);

        // --------------------------------------------------------
        // Mixed Content
        // --------------------------------------------------------

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.LOLLIPOP
        ) {

            settings.setMixedContentMode(
                    WebSettings
                            .MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        // --------------------------------------------------------
        // Desktop User-Agent
        // --------------------------------------------------------

        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) "
                        + "Chrome/140.0.0.0 "
                        + "Safari/537.36"
        );

        // --------------------------------------------------------
        // Cookie
        // --------------------------------------------------------

        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(
                true
        );

        cookieManager.setAcceptThirdPartyCookies(
                webView,
                true
        );

        // ========================================================
        // 最关键：
        // Document Start Blob Hook
        // ========================================================

        installDocumentStartBlobHook();

        // ========================================================
        // JavaScript Bridge
        // ========================================================

        webView.addJavascriptInterface(
                new BlobDownloadBridge(),
                "AndroidBlob"
        );

        // ========================================================
        // WebViewClient
        // ========================================================

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            Bitmap favicon
                    ) {

                        if (progressBar != null) {

                            progressBar.setVisibility(
                                    View.VISIBLE
                            );
                        }

                        /*
                         * Document Start 已经负责安装 Hook。
                         *
                         * 这里仅保留 fallback。
                         */

                        if (
                                !isDocumentStartSupported()
                        ) {

                            injectBlobInterceptorFallback(
                                    view
                            );
                        }
                    }

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        if (progressBar != null) {

                            progressBar.setVisibility(
                                    View.GONE
                            );
                        }

                        injectZoomFix(view);

                        injectResizeFix(view);

                        /*
                         * 只有不支持 Document Start
                         * 时才尝试备用 Hook。
                         */
                        if (
                                !isDocumentStartSupported()
                        ) {

                            injectBlobInterceptorFallback(
                                    view
                            );
                        }
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        return false;
                    }
                }
        );

        // ========================================================
        // WebChromeClient
        // ========================================================

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (
                                filePathCallback != null
                        ) {

                            filePathCallback
                                    .onReceiveValue(
                                            null
                                    );
                        }

                        filePathCallback =
                                callback;

                        Intent intent;

                        try {

                            intent =
                                    params.createIntent();

                        } catch (Exception e) {

                            intent =
                                    new Intent(
                                            Intent.ACTION_OPEN_DOCUMENT
                                    );

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType(
                                    "*/*"
                            );
                        }

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        try {

                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER_REQUEST
                            );

                        } catch (
                                ActivityNotFoundException e
                        ) {

                            filePathCallback = null;

                            Toast.makeText(
                                    SRToolsActivity.this,
                                    "找不到文件选择器",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return false;
                        }

                        return true;
                    }
                }
        );

        // ========================================================
        // Download Listener
        // ========================================================

        webView.setDownloadListener(
                new DownloadListener() {

                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength
                    ) {

                        if (
                                url == null
                                        || url.trim().isEmpty()
                        ) {

                            Toast.makeText(
                                    SRToolsActivity.this,
                                    "下载地址为空",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        // ------------------------------------------------
                        // Blob
                        // ------------------------------------------------

                        if (
                                url.startsWith("blob:")
                        ) {

                            String js =
                                    "window.__pearlDownloadBlob && "
                                            + "window.__pearlDownloadBlob("
                                            + quoteJs(url)
                                            + ","
                                            + quoteJs(
                                            mimeType == null
                                                    ? "application/octet-stream"
                                                    : mimeType
                                    )
                                            + ");";

                            webView.evaluateJavascript(
                                    js,
                                    null
                            );

                            return;
                        }

                        // ------------------------------------------------
                        // HTTP / HTTPS
                        // ------------------------------------------------

                        downloadHttpFile(
                                url,
                                contentDisposition,
                                mimeType
                        );
                    }
                }
        );
    }

    // ============================================================
    // Document Start Support
    // ============================================================

    private boolean isDocumentStartSupported() {

        try {

            return WebViewFeature.isFeatureSupported(
                    WebViewFeature.DOCUMENT_START_SCRIPT
            );

        } catch (Exception e) {

            return false;
        }
    }

    // ============================================================
    // 安装 Document Start Blob Hook
    // ============================================================

    private void installDocumentStartBlobHook() {

        if (webView == null) {
            return;
        }

        if (!isDocumentStartSupported()) {

            return;
        }

        try {

            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    DOCUMENT_START_BLOB_SCRIPT,
                    Collections.singleton("*")
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Blob Document Start 初始化失败："
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // Viewport
    // ============================================================

    private void injectZoomFix(
            WebView view
    ) {

        String javascript =

                "(function(){"

                        + "try{"

                        + "var metas="
                        + "document.getElementsByTagName('meta');"

                        + "var viewport=null;"

                        + "for(var i=0;i<metas.length;i++){"

                        + "if(metas[i].name && "
                        + "metas[i].name.toLowerCase()==='viewport'){"

                        + "viewport=metas[i];"
                        + "break;"

                        + "}"

                        + "}"

                        + "if(!viewport){"

                        + "viewport="
                        + "document.createElement('meta');"

                        + "viewport.name='viewport';"

                        + "(document.head || "
                        + "document.documentElement)"
                        + ".appendChild(viewport);"

                        + "}"

                        + "viewport.setAttribute("
                        + "'content',"
                        + "'width=" + WEB_PAGE_WIDTH + ","
                        + "initial-scale=1.0,"
                        + "minimum-scale=0.1,"
                        + "maximum-scale=5.0,"
                        + "user-scalable=yes'"
                        + ");"

                        + "}catch(e){}"

                        + "})();";

        view.evaluateJavascript(
                javascript,
                null
        );
    }

    // ============================================================
    // Resize
    // ============================================================

    private void injectResizeFix(
            WebView view
    ) {

        String javascript =

                "(function(){"

                        + "try{"

                        + "window.dispatchEvent("
                        + "new Event('resize')"
                        + ");"

                        + "if(document.documentElement){"

                        + "document.documentElement.style.margin='0';"

                        + "}"

                        + "if(document.body){"

                        + "document.body.style.margin='0';"

                        + "}"

                        + "}catch(e){}"

                        + "})();";

        view.evaluateJavascript(
                javascript,
                null
        );
    }

    // ============================================================
    // Fallback Blob Hook
    // ============================================================

    private void injectBlobInterceptorFallback(
            WebView view
    ) {

        String javascript =

                "(function(){"

                        + "try{"

                        + "if(window.__pearlBlobHookInstalled){"
                        + "return;"
                        + "}"

                        + "window.__pearlBlobHookInstalled=true;"

                        + "window.__pearlBlobMap=new Map();"

                        + "window.__pearlLastDownloadName='';"

                        + "var oldCreate="
                        + "URL.createObjectURL;"

                        + "var oldRevoke="
                        + "URL.revokeObjectURL;"

                        + "URL.createObjectURL=function(obj){"

                        + "var u="
                        + "oldCreate.call(URL,obj);"

                        + "window.__pearlBlobMap.set(u,obj);"

                        + "return u;"

                        + "};"

                        + "URL.revokeObjectURL=function(u){"

                        + "try{"
                        + "window.__pearlBlobMap.delete(u);"
                        + "}catch(e){}"

                        + "return oldRevoke.call(URL,u);"

                        + "};"

                        + "document.addEventListener("
                        + "'click',"
                        + "function(event){"

                        + "try{"

                        + "var element=event.target;"

                        + "while(element && "
                        + "element!==document){"

                        + "if(element.tagName==='A'){"

                        + "if(element.href && "
                        + "element.href.indexOf('blob:')===0 && "
                        + "element.download){"

                        + "window.__pearlLastDownloadName="
                        + "element.download;"

                        + "}"

                        + "break;"

                        + "}"

                        + "element=element.parentElement;"

                        + "}"

                        + "}catch(e){}"

                        + "},true);"

                        + "window.__pearlDownloadBlob="
                        + "function(url,mimeType){"

                        + "try{"

                        + "var blob="
                        + "window.__pearlBlobMap.get(url);"

                        + "if(!blob){"

                        + "AndroidBlob.abortBlob("
                        + "'找不到 Blob 对象'"
                        + ");"

                        + "return false;"

                        + "}"

                        + "var fileName="
                        + "window.__pearlLastDownloadName"
                        + "||'download.bin';"

                        + "AndroidBlob.beginBlob("
                        + "fileName,"
                        + "mimeType||blob.type||"
                        + "'application/octet-stream',"
                        + "blob.size"
                        + ");"

                        + "if(blob.stream){"

                        + "var reader="
                        + "blob.stream().getReader();"

                        + "var next=function(){"

                        + "reader.read().then(function(result){"

                        + "if(result.done){"

                        + "AndroidBlob.finishBlob();"
                        + "return;"
                        + "}"

                        + "var bytes=result.value;"
                        + "var step=32768;"

                        + "for(var s=0;"
                        + "s<bytes.length;"
                        + "s+=step){"

                        + "var part="
                        + "bytes.subarray("
                        + "s,"
                        + "Math.min(s+step,bytes.length)"
                        + ");"

                        + "var binary='';"

                        + "for(var i=0;i<part.length;i++){"

                        + "binary+="
                        + "String.fromCharCode(part[i]);"

                        + "}"

                        + "AndroidBlob.receiveBlobChunk("
                        + "btoa(binary)"
                        + ");"

                        + "}"

                        + "next();"

                        + "}).catch(function(error){"

                        + "AndroidBlob.abortBlob("
                        + "String(error)"
                        + ");"

                        + "});"

                        + "};"

                        + "next();"

                        + "}else{"

                        + "var reader="
                        + "new FileReader();"

                        + "reader.onloadend=function(){"

                        + "var result="
                        + "reader.result;"

                        + "var base64="
                        + "result.split(',')[1];"

                        + "var step=65536;"

                        + "for(var p=0;"
                        + "p<base64.length;"
                        + "p+=step){"

                        + "AndroidBlob.receiveBase64Chunk("
                        + "base64.substring(p,p+step)"
                        + ");"

                        + "}"

                        + "AndroidBlob.finishBlob();"

                        + "};"

                        + "reader.readAsDataURL(blob);"

                        + "}"

                        + "}catch(e){"

                        + "AndroidBlob.abortBlob("
                        + "String(e)"
                        + ");"

                        + "}"

                        + "};"

                        + "}catch(e){}"

                        + "})();";

        view.evaluateJavascript(
                javascript,
                null
        );
    }

    // ============================================================
    // HTTP / HTTPS 下载
    // ============================================================

    private void downloadHttpFile(
            String url,
            String contentDisposition,
            String mimeType
    ) {

        String fileName =
                extractFileName(
                        contentDisposition,
                        url
                );

        if (
                fileName == null
                        || fileName.trim().isEmpty()
        ) {

            fileName = "download.bin";
        }

        fileName =
                sanitizeFileName(
                        fileName
                );

        // ========================================================
        // Android 10+
        // ========================================================

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.Q
        ) {

            ContentValues values =
                    new ContentValues();

            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    fileName
            );

            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    mimeType == null
                            ? "application/octet-stream"
                            : mimeType
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

            if (uri == null) {

                Toast.makeText(
                        this,
                        "创建下载文件失败",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            try {

                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );

                request.setTitle(fileName);

                request.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                request.setDestinationUri(
                        uri
                );

                DownloadManager dm =
                        (DownloadManager)
                                getSystemService(
                                        DOWNLOAD_SERVICE
                                );

                dm.enqueue(request);

                Toast.makeText(
                        this,
                        "已开始下载：Download/"
                                + DOWNLOAD_FOLDER
                                + "/"
                                + fileName,
                        Toast.LENGTH_SHORT
                ).show();

            } catch (Exception e) {

                getContentResolver().delete(
                        uri,
                        null,
                        null
                );

                Toast.makeText(
                        this,
                        "下载失败："
                                + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        // ========================================================
        // Android 9-
        // ========================================================

        File dir =
                new File(
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                ),
                        DOWNLOAD_FOLDER
                );

        if (
                !dir.exists()
                        && !dir.mkdirs()
        ) {

            Toast.makeText(
                    this,
                    "无法创建下载目录",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setTitle(fileName);

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    DOWNLOAD_FOLDER
                            + "/"
                            + fileName
            );

            DownloadManager dm =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            dm.enqueue(request);

            Toast.makeText(
                    this,
                    "已开始下载：Download/"
                            + DOWNLOAD_FOLDER
                            + "/"
                            + fileName,
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "下载失败："
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // Extract filename
    // ============================================================

    private String extractFileName(
            String contentDisposition,
            String url
    ) {

        try {

            if (contentDisposition != null) {

                String lower =
                        contentDisposition.toLowerCase(
                                Locale.US
                        );

                // ------------------------------------------------
                // filename*
                // ------------------------------------------------

                int p =
                        lower.indexOf(
                                "filename*="
                        );

                if (p >= 0) {

                    String value =
                            contentDisposition
                                    .substring(
                                            p + 10
                                    )
                                    .trim();

                    int semi =
                            value.indexOf(";");

                    if (semi >= 0) {

                        value =
                                value.substring(
                                        0,
                                        semi
                                ).trim();
                    }

                    int apos =
                            value.indexOf("''");

                    if (apos >= 0) {

                        value =
                                value.substring(
                                        apos + 2
                                );
                    }

                    value =
                            Uri.decode(
                                    value.replace(
                                            "\"",
                                            ""
                                    )
                            );

                    if (!value.isEmpty()) {

                        return sanitizeFileName(
                                value
                        );
                    }
                }

                // ------------------------------------------------
                // filename
                // ------------------------------------------------

                p =
                        lower.indexOf(
                                "filename="
                        );

                if (p >= 0) {

                    String value =
                            contentDisposition
                                    .substring(
                                            p + 9
                                    )
                                    .trim();

                    value =
                            value.replace(
                                    "\"",
                                    ""
                            ).replace(
                                    "'",
                                    ""
                            );

                    int semi =
                            value.indexOf(";");

                    if (semi >= 0) {

                        value =
                                value.substring(
                                        0,
                                        semi
                                ).trim();
                    }

                    if (!value.isEmpty()) {

                        return sanitizeFileName(
                                value
                        );
                    }
                }
            }

            // ----------------------------------------------------
            // URL filename
            // ----------------------------------------------------

            Uri uri =
                    Uri.parse(url);

            String path =
                    uri.getPath();

            if (path != null) {

                int slash =
                        path.lastIndexOf('/');

                if (slash >= 0) {

                    path =
                            path.substring(
                                    slash + 1
                            );
                }

                if (!path.isEmpty()) {

                    return sanitizeFileName(
                            Uri.decode(path)
                    );
                }
            }

        } catch (Exception ignored) {
        }

        return "download.bin";
    }

    // ============================================================
    // Sanitize filename
    // ============================================================

    private String sanitizeFileName(
            String name
    ) {

        if (name == null) {
            return "download.bin";
        }

        name =
                name.replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                ).trim();

        if (name.isEmpty()) {
            return "download.bin";
        }

        return name;
    }

    // ============================================================
    // JS quote
    // ============================================================

    private String quoteJs(
            String value
    ) {

        if (value == null) {
            return "null";
        }

        return "'"
                + value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "'",
                        "\\'"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                + "'";
    }

    // ============================================================
    // Legacy Blob file
    // ============================================================

    private Uri createLegacyBlobFile(
            String fileName
    ) {

        File dir =
                new File(
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                ),
                        DOWNLOAD_FOLDER
                );

        if (
                !dir.exists()
                        && !dir.mkdirs()
        ) {

            return null;
        }

        File file =
                new File(
                        dir,
                        fileName
                );

        int i = 1;

        String base =
                fileName;

        String ext = "";

        int dot =
                fileName.lastIndexOf('.');

        if (dot > 0) {

            base =
                    fileName.substring(
                            0,
                            dot
                    );

            ext =
                    fileName.substring(
                            dot
                    );
        }

        while (file.exists()) {

            file =
                    new File(
                            dir,
                            base
                                    + " ("
                                    + i
                                    + ")"
                                    + ext
                    );

            i++;
        }

        blobTempFile = file;

        return Uri.fromFile(
                file
        );
    }

    // ============================================================
    // Modern Blob
    // ============================================================

    private Uri beginModernBlobFile(
            String fileName,
            String mimeType
    ) {

        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.Downloads.DISPLAY_NAME,
                fileName
        );

        values.put(
                MediaStore.Downloads.MIME_TYPE,
                mimeType == null
                        ? "application/octet-stream"
                        : mimeType
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

        return getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );
    }

    // ============================================================
    // Cleanup Blob
    // ============================================================

    private synchronized void cleanupBlobDownload() {

        try {

            if (blobOutputStream != null) {

                blobOutputStream.close();
            }

        } catch (Exception ignored) {
        }

        blobOutputStream = null;

        // --------------------------------------------------------
        // MediaStore
        // --------------------------------------------------------

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

        blobMediaStoreUri = null;

        // --------------------------------------------------------
        // Android 9-
        // --------------------------------------------------------

        if (
                blobTempFile != null
                        && blobTempFile.exists()
        ) {

            //noinspection ResultOfMethodCallIgnored
            blobTempFile.delete();
        }

        blobTempFile = null;

        blobFileName = null;

        blobMimeType = null;

        blobExpectedSize = -1L;

        blobWrittenSize = 0L;
    }

    // ============================================================
    // Blob Bridge
    // ============================================================

    private class BlobDownloadBridge {

        // --------------------------------------------------------
        // 开始 Blob
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void beginBlob(
                String fileName,
                String mimeType,
                long size
        ) {

            cleanupBlobDownload();

            blobFileName =
                    sanitizeFileName(
                            fileName
                    );

            blobMimeType =
                    mimeType == null
                            ? "application/octet-stream"
                            : mimeType;

            blobExpectedSize = size;

            blobWrittenSize = 0L;

            try {

                // ------------------------------------------------
                // Android 10+
                // ------------------------------------------------

                if (
                        Build.VERSION.SDK_INT
                                >= Build.VERSION_CODES.Q
                ) {

                    blobMediaStoreUri =
                            beginModernBlobFile(
                                    blobFileName,
                                    blobMimeType
                            );

                    if (
                            blobMediaStoreUri == null
                    ) {

                        throw new Exception(
                                "无法创建 MediaStore 文件"
                        );
                    }

                    blobOutputStream =
                            getContentResolver()
                                    .openOutputStream(
                                            blobMediaStoreUri
                                    );

                } else {

                    // ------------------------------------------------
                    // Android 9-
                    // ------------------------------------------------

                    Uri uri =
                            createLegacyBlobFile(
                                    blobFileName
                            );

                    if (
                            uri == null
                                    || blobTempFile == null
                    ) {

                        throw new Exception(
                                "无法创建下载文件"
                        );
                    }

                    blobOutputStream =
                            new FileOutputStream(
                                    blobTempFile
                            );
                }

                if (
                        blobOutputStream == null
                ) {

                    throw new Exception(
                            "下载文件没有打开"
                    );
                }

            } catch (Exception e) {

                cleanupBlobDownload();

                String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                runOnUiThread(
                        () ->
                                Toast.makeText(
                                        SRToolsActivity.this,
                                        "创建下载文件失败："
                                                + message,
                                        Toast.LENGTH_LONG
                                ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Chunk
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void receiveBlobChunk(
                String base64
        ) {

            if (
                    blobOutputStream == null
            ) {

                return;
            }

            try {

                byte[] data;

                if (
                        Build.VERSION.SDK_INT
                                >= Build.VERSION_CODES.O
                ) {

                    data =
                            Base64.getDecoder()
                                    .decode(
                                            base64
                                    );

                } else {

                    data =
                            android.util.Base64.decode(
                                    base64,
                                    android.util.Base64.DEFAULT
                            );
                }

                blobOutputStream.write(
                        data
                );

                blobWrittenSize +=
                        data.length;

            } catch (Exception e) {

                String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                cleanupBlobDownload();

                runOnUiThread(
                        () ->
                                Toast.makeText(
                                        SRToolsActivity.this,
                                        "写入下载文件失败："
                                                + message,
                                        Toast.LENGTH_LONG
                                ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Fallback Base64 chunk
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void receiveBase64Chunk(
                String base64
        ) {

            receiveBlobChunk(
                    base64
            );
        }

        // --------------------------------------------------------
        // Finish
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void finishBlob() {

            if (
                    blobOutputStream == null
            ) {

                return;
            }

            String completedFileName =
                    blobFileName == null
                            ? "download.bin"
                            : blobFileName;

            try {

                blobOutputStream.flush();

                blobOutputStream.close();

                blobOutputStream = null;

                // ------------------------------------------------
                // 检查大小
                // ------------------------------------------------

                if (
                        blobExpectedSize >= 0
                                && blobWrittenSize
                                != blobExpectedSize
                ) {

                    cleanupBlobDownload();

                    throw new Exception(
                            "Blob 大小不一致："
                                    + blobWrittenSize
                                    + "/"
                                    + blobExpectedSize
                    );
                }

                // ------------------------------------------------
                // Android 10+
                // ------------------------------------------------

                if (
                        Build.VERSION.SDK_INT
                                >= Build.VERSION_CODES.Q
                ) {

                    if (
                            blobMediaStoreUri != null
                    ) {

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

                blobMediaStoreUri = null;

                blobTempFile = null;

                blobFileName = null;

                blobMimeType = null;

                blobExpectedSize = -1L;

                blobWrittenSize = 0L;

                runOnUiThread(
                        () ->
                                Toast.makeText(
                                        SRToolsActivity.this,
                                        "下载完成：Download/"
                                                + DOWNLOAD_FOLDER
                                                + "/"
                                                + completedFileName,
                                        Toast.LENGTH_LONG
                                ).show()
                );

            } catch (Exception e) {

                cleanupBlobDownload();

                String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                runOnUiThread(
                        () ->
                                Toast.makeText(
                                        SRToolsActivity.this,
                                        "完成下载失败："
                                                + message,
                                        Toast.LENGTH_LONG
                                ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Abort
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void abortBlob(
                String reason
        ) {

            cleanupBlobDownload();

            String message =
                    reason == null
                            ? "未知错误"
                            : reason;

            runOnUiThread(
                    () ->
                            Toast.makeText(
                                    SRToolsActivity.this,
                                    "Blob 下载失败："
                                            + message,
                                    Toast.LENGTH_LONG
                            ).show()
            );
        }
    }

    // ============================================================
    // File Chooser Result
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
                requestCode
                        != FILE_CHOOSER_REQUEST
        ) {

            return;
        }

        Uri[] results = null;

        if (
                resultCode == Activity.RESULT_OK
                        && data != null
        ) {

            // ----------------------------------------------------
            // Multiple
            // ----------------------------------------------------

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

            }

            // ----------------------------------------------------
            // Single
            // ----------------------------------------------------

            else if (
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

    // ============================================================
    // Back
    // ============================================================

    @Override
    public void onBackPressed() {

        if (
                webView != null
                        && webView.canGoBack()
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

        if (
                filePathCallback != null
        ) {

            filePathCallback
                    .onReceiveValue(
                            null
                    );

            filePathCallback = null;
        }

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