package com.reversedrooms.pearlserver;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Locale;

public class SRToolsActivity extends Activity {

    private static final String SRTOOLS_URL =
            "https://srtools.neonteam.dev/1001/detail";

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int WRITE_PERMISSION_REQUEST = 1002;

    private static final int WEB_PAGE_WIDTH = 1280;

    private WebView webView;
    private ProgressBar progressBar;

    private Button orientationButton;
    private Button reloadButton;
    private Button closeButton;

    private ValueCallback<Uri[]> filePathCallback;

    private boolean landscape = false;
    private boolean documentStartHookInstalled;
    private String syncResponseScript = "";

    private final android.os.Handler mainHandler =
            new android.os.Handler(
                    android.os.Looper.getMainLooper()
            );

    private String pendingHttpUrl;
    private String pendingHttpUserAgent;
    private String pendingHttpFileName;
    private String pendingHttpMimeType;


    // ============================================================
    // Activity
    // ============================================================

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(
                R.layout.activity_srtools
        );

        webView = findViewById(
                R.id.webView
        );

        progressBar = findViewById(
                R.id.progressBar
        );

        orientationButton = findViewById(
                R.id.orientationButton
        );

        reloadButton = findViewById(
                R.id.reloadButton
        );

        closeButton = findViewById(
                R.id.closeButton
        );

        if (!ServerStorage.hasAccess(this)) {
            Toast.makeText(this, "请返回主页授权存储访问后再打开 SRTools", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupButtons();

        try (Reader reader = new InputStreamReader(
                getAssets().open("srtools-sync.js"), StandardCharsets.UTF_8)) {
            StringBuilder script = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                script.append(buffer, 0, count);
            }
            syncResponseScript = script.toString();
        } catch (IOException e) {
            Toast.makeText(this, "SRTools 同步检查初始化失败，请重新安装应用", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupWebView();

        webView.loadUrl(
                SRTOOLS_URL
        );
    }


    // ============================================================
    // Buttons
    // ============================================================

    private void setupButtons() {

        if (orientationButton != null) {

            orientationButton.setOnClickListener(
                    v -> toggleOrientation()
            );
        }

        if (reloadButton != null) {

            reloadButton.setOnClickListener(
                    v -> {

                        if (webView != null) {
                            webView.reload();
                        }
                    }
            );
        }

        if (closeButton != null) {

            closeButton.setOnClickListener(
                    v -> finish()
            );
        }

        updateOrientationButton();
    }


    private void toggleOrientation() {

        if (landscape) {

            landscape = false;

            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            );

        } else {

            landscape = true;

            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            );
        }

        updateOrientationButton();
    }


    private void updateOrientationButton() {

        if (orientationButton == null) {
            return;
        }

        orientationButton.setText(
                landscape
                        ? getString(R.string.srtools_orientation_landscape)
                        : getString(R.string.srtools_orientation_portrait)
        );
    }


    // ============================================================
    // WebView
    // ============================================================

    private void setupWebView() {

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(
                true
        );

        settings.setSupportMultipleWindows(
                true
        );

        settings.setBuiltInZoomControls(
                true
        );

        settings.setDisplayZoomControls(
                false
        );

        settings.setSupportZoom(
                true
        );

        settings.setUseWideViewPort(
                true
        );

        settings.setLoadWithOverviewMode(
                true
        );

        settings.setTextZoom(
                100
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP) {

            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            );
        }

        /*
         * Desktop Chrome UA
         */
        settings.setUserAgentString(
                "Mozilla/5.0 (X11; Linux x86_64) "
                        + "AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) "
                        + "Chrome/140.0.0.0 "
                        + "Safari/537.36"
        );


        // ========================================================
        // Cookie
        // ========================================================

        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(
                true
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP) {

            cookieManager.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }


        // ========================================================
        // Blob Bridge
        // ========================================================

        webView.addJavascriptInterface(
                new BlobDownloadBridge(),
                "AndroidBlob"
        );


        // ========================================================
        // Document Start Blob Hook
        // ========================================================

        installDocumentStartBlobHook();


        // ========================================================
        // WebViewClient
        // ========================================================

        webView.setWebViewClient(
                new WebViewClient() {

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

                        showProgress(true);

                        /*
                         * 如果 WebView 不支持
                         * Document Start Script，
                         * 使用普通注入作为兼容方案。
                         */
                        if (!documentStartHookInstalled) {

                            injectBlobHook(view);
                        }
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

                        showProgress(false);

                        /*
                         * 兼容旧 WebView。
                         */
                        if (!documentStartHookInstalled) {

                            injectBlobHook(view);
                        }

                        injectViewport(view);
                    }


                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        Uri uri =
                                request.getUrl();

                        if (uri == null) {
                            return false;
                        }

                        String scheme =
                                uri.getScheme();

                        if (scheme == null) {
                            return false;
                        }

                        /*
                         * WebView 自己处理。
                         */
                        if ("http".equalsIgnoreCase(scheme)
                                || "https".equalsIgnoreCase(scheme)
                                || "blob".equalsIgnoreCase(scheme)) {

                            return false;
                        }

                        /*
                         * 其他协议交给系统。
                         */
                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            uri
                                    );

                            startActivity(intent);

                            return true;

                        } catch (
                                ActivityNotFoundException e
                        ) {

                            showToast(
                                    "无法打开链接"
                            );

                            return true;
                        }
                    }


                    @Override
                    public WebResourceResponse
                    shouldInterceptRequest(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        return super.shouldInterceptRequest(
                                view,
                                request
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

                        showProgress(
                                newProgress < 100
                        );
                    }


                    @Override
                    public boolean onJsAlert(
                            WebView view,
                            String url,
                            String message,
                            JsResult result
                    ) {

                        return super.onJsAlert(
                                view,
                                url,
                                message,
                                result
                        );
                    }


                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (filePathCallback != null) {

                            filePathCallback
                                    .onReceiveValue(
                                            null
                                    );
                        }

                        filePathCallback =
                                callback;

                        openFileChooser(
                                params
                        );

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

                        if (url == null) {
                            return;
                        }


                        // ------------------------------------------------
                        // Blob
                        // ------------------------------------------------

                        if (url.startsWith("blob:")) {

                            String safeMime =
                                    mimeType;

                            if (safeMime == null
                                    || safeMime
                                    .trim()
                                    .isEmpty()) {

                                safeMime =
                                        "application/octet-stream";
                            }

                            String js =
                                    "javascript:(function(){"
                                            + "try{"
                                            + "if(window.__pearlDownloadBlob){"
                                            + "window.__pearlDownloadBlob("
                                            + jsString(url)
                                            + ","
                                            + jsString(safeMime)
                                            + ",null"
                                            + ");"
                                            + "}"
                                            + "}catch(e){"
                                            + "console.error(e);"
                                            + "}"
                                            + "})()";

                            webView.evaluateJavascript(
                                    js,
                                    null
                            );

                            return;
                        }


                        // ------------------------------------------------
                        // HTTP / HTTPS
                        // ------------------------------------------------

                        if (url.startsWith("http://")
                                || url.startsWith("https://")) {

                            downloadHttpFile(
                                    url,
                                    userAgent,
                                    contentDisposition,
                                    mimeType
                            );

                            return;
                        }


                        showToast(
                                "不支持的下载地址"
                        );
                    }
                }
        );
    }


    // ============================================================
    // Blob Document Start Script
    // ============================================================

    private static final String DOCUMENT_START_BLOB_SCRIPT =

            "(function(){"

                    + "if(window.__pearlBlobHookInstalled){return;}"

                    + "window.__pearlBlobHookInstalled=true;"

                    /*
                     * Blob URL -> Blob
                     */
                    + "window.__pearlBlobMap=new Map();"

                    /*
                     * 正在处理的 Blob URL
                     */
                    + "window.__pearlBlobActive=new Set();"


                    // ------------------------------------------------
                    // 文件名清理
                    // ------------------------------------------------

                    + "window.__pearlBlobSafeName=function(name){"
                    + "try{"
                    + "name=String(name||'download.bin');"
                    + "name=name.replace(/[\\\\/:*?\"<>|]/g,'_');"
                    + "name=name.trim();"
                    + "if(!name){name='download.bin';}"
                    + "return name;"
                    + "}catch(e){"
                    + "return 'download.bin';"
                    + "}"
                    + "};"


                    // ------------------------------------------------
                    // createObjectURL
                    // ------------------------------------------------

                    + "var oldCreate=URL.createObjectURL;"

                    + "URL.createObjectURL=function(obj){"

                    + "var u=oldCreate.apply(this,arguments);"

                    + "try{"

                    + "if(obj instanceof Blob){"

                    + "window.__pearlBlobMap.set(u,obj);"

                    + "}"

                    + "}catch(e){}"

                    + "return u;"

                    + "};"


                    // ------------------------------------------------
                    // revokeObjectURL
                    // ------------------------------------------------

                    /*
                     * 不立即删除。
                     *
                     * 很多网页：
                     *
                     * a.click();
                     * URL.revokeObjectURL(url);
                     *
                     * 如果立即删除，Android 可能还没来得及
                     * 处理 DownloadListener。
                     */

                    + "var oldRevoke=URL.revokeObjectURL;"

                    + "URL.revokeObjectURL=function(u){"

                    + "try{"

                    + "setTimeout(function(){"

                    + "try{"

                    + "window.__pearlBlobMap.delete(u);"

                    + "}catch(e){}"

                    + "},30000);"

                    + "}catch(e){}"

                    + "return oldRevoke.apply(this,arguments);"

                    + "};"


                    // ------------------------------------------------
                    // 发送 Blob 到 Android
                    // ------------------------------------------------

                    + "window.__pearlSendBlob="
                    + "function(blob,name,mime){"

                    + "try{"

                    + "name=window.__pearlBlobSafeName(name);"

                    + "mime=mime||blob.type||"
                    + "'application/octet-stream';"

                    + "if(!window.AndroidBlob){"

                    + "throw new Error("
                    + "'AndroidBlob接口不存在'"
                    + ");"

                    + "}"


                    /*
                     * 必须同步 beginBlob。
                     */

                    + "AndroidBlob.beginBlob("
                    + "name,"
                    + "mime,"
                    + "blob.size"
                    + ");"


                    // ------------------------------------------------
                    // Blob.stream
                    // ------------------------------------------------

                    + "if(blob.stream){"

                    + "var reader="
                    + "blob.stream().getReader();"

                    + "var readNext=function(){"

                    + "reader.read().then(function(r){"

                    + "if(r.done){"

                    + "AndroidBlob.finishBlob();"

                    + "return;"

                    + "}"

                    + "var bytes="
                    + "new Uint8Array(r.value);"

                    + "var chunkSize=32768;"

                    + "for(var i=0;"
                    + "i<bytes.length;"
                    + "i+=chunkSize){"

                    + "var end=Math.min("
                    + "i+chunkSize,"
                    + "bytes.length"
                    + ");"

                    + "var part="
                    + "bytes.slice(i,end);"

                    + "var binary='';"

                    + "for(var j=0;"
                    + "j<part.length;"
                    + "j++){"

                    + "binary+="
                    + "String.fromCharCode(part[j]);"

                    + "}"

                    + "AndroidBlob.writeBlobChunk("
                    + "btoa(binary)"
                    + ");"

                    + "}"

                    + "readNext();"

                    + "}).catch(function(e){"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "String(e)"
                    + ");"

                    + "}catch(x){}"

                    + "});"

                    + "};"

                    + "readNext();"

                    + "return;"

                    + "}"


                    // ------------------------------------------------
                    // FileReader fallback
                    // ------------------------------------------------

                    + "var fr=new FileReader();"

                    + "fr.onload=function(){"

                    + "try{"

                    + "var data=fr.result||'';"

                    + "var comma=data.indexOf(',');"

                    + "if(comma>=0){"

                    + "data=data.substring(comma+1);"

                    + "}"

                    + "var chunk=32768;"

                    + "for(var i=0;"
                    + "i<data.length;"
                    + "i+=chunk){"

                    + "AndroidBlob.writeBlobChunk("
                    + "data.substring("
                    + "i,"
                    + "Math.min(i+chunk,data.length)"
                    + ")"
                    + ");"

                    + "}"

                    + "AndroidBlob.finishBlob();"

                    + "}catch(e){"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "String(e)"
                    + ");"

                    + "}catch(x){}"

                    + "}"

                    + "};"


                    + "fr.onerror=function(){"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "'FileReader失败'"
                    + ");"

                    + "}catch(e){}"

                    + "};"


                    + "fr.readAsDataURL(blob);"

                    + "}catch(e){"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "String(e)"
                    + ");"

                    + "}catch(x){}"

                    + "}"

                    + "};"


                    // ------------------------------------------------
                    // Blob URL 下载
                    // ------------------------------------------------

                    + "window.__pearlDownloadBlob="
                    + "function(url,mime,name){"

                    + "try{"

                    + "if(!url||"
                    + "String(url).indexOf('blob:')!==0){"

                    + "throw new Error("
                    + "'不是Blob URL'"
                    + ");"

                    + "}"

                    + "url=String(url);"


                    /*
                     * 防止重复下载。
                     */

                    + "if(window.__pearlBlobActive.has(url)){"

                    + "return;"

                    + "}"

                    + "window.__pearlBlobActive.add(url);"


                    + "var finishGuard=function(){"

                    + "setTimeout(function(){"

                    + "try{"

                    + "window.__pearlBlobActive.delete(url);"

                    + "}catch(e){}"

                    + "},10000);"

                    + "};"


                    /*
                     * 第一优先级：
                     * Map
                     */

                    + "var mapped=null;"

                    + "try{"

                    + "mapped="
                    + "window.__pearlBlobMap.get(url);"

                    + "}catch(e){}"


                    + "if(mapped){"

                    + "window.__pearlSendBlob("
                    + "mapped,"
                    + "name||'download.bin',"
                    + "mime||mapped.type"
                    + ");"

                    + "finishGuard();"

                    + "return;"

                    + "}"


                    /*
                     * 第二优先级：
                     * fetch(blob:)
                     */

                    + "fetch(url).then(function(resp){"

                    + "if(!resp.ok){"

                    + "throw new Error("
                    + "'Blob fetch失败: '+resp.status"
                    + ");"

                    + "}"

                    + "return resp.blob();"

                    + "}).then(function(blob){"

                    + "window.__pearlSendBlob("
                    + "blob,"
                    + "name||'download.bin',"
                    + "mime||blob.type"
                    + ");"

                    + "finishGuard();"

                    + "}).catch(function(e){"

                    + "finishGuard();"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "'Blob下载失败: '+String(e)"
                    + ");"

                    + "}catch(x){}"

                    + "});"

                    + "}catch(e){"

                    + "try{"

                    + "AndroidBlob.abortBlob("
                    + "String(e)"
                    + ");"

                    + "}catch(x){}"

                    + "}"

                    + "};"


                    // ------------------------------------------------
                    // Anchor Blob
                    // ------------------------------------------------

                    + "window.__pearlHandleBlobAnchor="
                    + "function(a){"

                    + "try{"

                    + "if(!a){return false;}"

                    + "var href="
                    + "a.href||"
                    + "a.getAttribute('href')||'';"

                    + "if(!href||"
                    + "String(href).indexOf('blob:')!==0){"

                    + "return false;"

                    + "}"

                    + "var name="
                    + "a.download||"
                    + "a.getAttribute('download')||"
                    + "'download.bin';"

                    + "window.__pearlDownloadBlob("
                    + "href,"
                    + "a.type||'',"
                    + "name"
                    + ");"

                    + "return true;"

                    + "}catch(e){"

                    + "return false;"

                    + "}"

                    + "};"


                    // ------------------------------------------------
                    // 用户点击 Blob Anchor
                    // ------------------------------------------------

                    + "document.addEventListener("
                    + "'click',function(ev){"

                    + "try{"

                    + "var node=ev.target;"

                    + "while(node&&node!==document){"

                    + "if(node.tagName&&"
                    + "node.tagName.toLowerCase()==='a'){"

                    + "var href="
                    + "node.href||"
                    + "node.getAttribute('href')||'';"

                    + "if(String(href)"
                    + ".indexOf('blob:')===0){"

                    + "window.__pearlHandleBlobAnchor("
                    + "node"
                    + ");"

                    + "ev.preventDefault();"

                    + "ev.stopImmediatePropagation();"

                    + "return;"

                    + "}"

                    + "break;"

                    + "}"

                    + "node=node.parentElement;"

                    + "}"

                    + "}catch(e){}"

                    + "},true);"


                    // ------------------------------------------------
                    // 拦截 a.click()
                    // ------------------------------------------------

                    /*
                     * 很多下载器实际使用：
                     *
                     * const a=document.createElement('a');
                     * a.href=URL.createObjectURL(blob);
                     * a.download='freesr-data.json';
                     * a.click();
                     */

                    + "try{"

                    + "var oldAnchorClick="
                    + "HTMLAnchorElement.prototype.click;"

                    + "HTMLAnchorElement.prototype.click="
                    + "function(){"

                    + "try{"

                    + "var href="
                    + "this.href||"
                    + "this.getAttribute('href')||'';"

                    + "if(String(href)"
                    + ".indexOf('blob:')===0){"

                    + "var name="
                    + "this.download||"
                    + "this.getAttribute('download')||"
                    + "'download.bin';"

                    + "window.__pearlDownloadBlob("
                    + "href,"
                    + "this.type||'',"
                    + "name"
                    + ");"

                    + "return;"

                    + "}"

                    + "}catch(e){}"

                    + "return oldAnchorClick.apply("
                    + "this,"
                    + "arguments"
                    + ");"

                    + "};"

                    + "}catch(e){}"

                    + "})();";


    // ============================================================
    // Document Start
    // ============================================================

    private void installDocumentStartBlobHook() {

        if (!supportsDocumentStartJavaScript()) {
            return;
        }

        try {

            /*
             * 注意：
             *
             * 第三个参数必须是 Set<String>
             * 不能传 Set<Uri>
             *
             * androidx.webkit 1.12.1：
             * Set<String> allowedOriginRules
             */

            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    DOCUMENT_START_BLOB_SCRIPT + syncResponseScript,
                    Collections.singleton(
                            "https://srtools.neonteam.dev"
                    )
            );
            documentStartHookInstalled = true;

        } catch (Throwable e) {

            e.printStackTrace();
        }
    }


    private boolean supportsDocumentStartJavaScript() {

        try {

            return WebViewFeature.isFeatureSupported(
                    WebViewFeature.DOCUMENT_START_SCRIPT
            );

        } catch (Throwable e) {

            return false;
        }
    }


    private void injectBlobHook(
            WebView view
    ) {
        Uri page = Uri.parse(view.getUrl() == null ? "" : view.getUrl());
        if (!"https".equals(page.getScheme())
                || !"srtools.neonteam.dev".equals(page.getHost())) {
            return;
        }

        try {

            view.evaluateJavascript(
                    DOCUMENT_START_BLOB_SCRIPT + syncResponseScript,
                    null
            );

        } catch (Throwable e) {

            e.printStackTrace();
        }
    }


    // ============================================================
    // Viewport
    // ============================================================

    private void injectViewport(
            WebView view
    ) {

        String js =

                "(function(){"
                        + "try{"

                        + "var meta="
                        + "document.querySelector("
                        + "'meta[name=\"viewport\"]'"
                        + ");"

                        + "if(!meta){"

                        + "meta="
                        + "document.createElement('meta');"

                        + "meta.name='viewport';"

                        + "document.head.appendChild(meta);"

                        + "}"

                        + "meta.content="
                        + "'width="
                        + WEB_PAGE_WIDTH
                        + ",initial-scale=1.0,"
                        + "minimum-scale=0.1,"
                        + "maximum-scale=5.0,"
                        + "user-scalable=yes';"

                        + "}catch(e){}"

                        + "})();";

        try {

            view.evaluateJavascript(
                    js,
                    null
            );

        } catch (Throwable e) {

            e.printStackTrace();
        }
    }


    // ============================================================
    // File chooser
    // ============================================================

    private void openFileChooser(
            WebChromeClient.FileChooserParams params
    ) {

        try {

            Intent intent;

            try {

                intent =
                        params.createIntent();

            } catch (Throwable e) {

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

            startActivityForResult(
                    intent,
                    FILE_CHOOSER_REQUEST
            );

        } catch (
                ActivityNotFoundException e
        ) {

            filePathCallback = null;

            showToast(
                    "没有可用的文件选择器"
            );
        }
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode !=
                FILE_CHOOSER_REQUEST) {

            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        if (resultCode == RESULT_OK
                && data != null) {

            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }

        filePathCallback
                .onReceiveValue(
                        results
                );

        filePathCallback = null;
    }


    // ============================================================
    // HTTP / HTTPS Download
    // ============================================================

    private void downloadHttpFile(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {

        try {

            String fileName =
                    guessFileName(
                            url,
                            contentDisposition,
                            mimeType
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                downloadWithMediaStore(
                        url,
                        userAgent,
                        fileName,
                        mimeType
                );

            } else {

                if (checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

                    pendingHttpUrl = url;
                    pendingHttpUserAgent = userAgent;
                    pendingHttpFileName = fileName;
                    pendingHttpMimeType = mimeType;

                    requestPermissions(
                            new String[]{
                                    Manifest.permission
                                            .WRITE_EXTERNAL_STORAGE
                            },
                            WRITE_PERMISSION_REQUEST
                    );

                    return;
                }

                startLegacyDownload(
                        url,
                        userAgent,
                        fileName,
                        mimeType
                );
            }

        } catch (Throwable e) {

            e.printStackTrace();

            showToast(
                    "下载失败：" +
                            safeMessage(e)
            );
        }
    }


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

        if (requestCode !=
                WRITE_PERMISSION_REQUEST) {

            return;
        }

        if (grantResults.length > 0
                && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED) {

            if (pendingHttpUrl != null) {

                startLegacyDownload(
                        pendingHttpUrl,
                        pendingHttpUserAgent,
                        pendingHttpFileName,
                        pendingHttpMimeType
                );
            }

        } else {

            showToast(
                    "没有存储权限，无法保存文件"
            );
        }

        pendingHttpUrl = null;
        pendingHttpUserAgent = null;
        pendingHttpFileName = null;
        pendingHttpMimeType = null;
    }


    private void startLegacyDownload(
            String url,
            String userAgent,
            String fileName,
            String mimeType
    ) {

        try {

            ServerStorage.downloadDirectory(fileName);

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            if (mimeType != null
                    && !mimeType.isEmpty()) {

                request.setMimeType(
                        mimeType
                );
            }

            request.setTitle(
                    fileName
            );

            request.setDescription(
                    "Pearl SR 下载"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setAllowedOverMetered(
                    true
            );

            request.setAllowedOverRoaming(
                    true
            );

            if (userAgent != null
                    && !userAgent.isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS
                            + File.separator
                            + ServerStorage.downloadFolder(fileName),
                    fileName
            );

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {

                manager.enqueue(
                        request
                );

                showToast(
                        "开始下载：" + fileName
                );
            }

        } catch (Throwable e) {

            e.printStackTrace();

            showToast(
                    "下载失败：" +
                            safeMessage(e)
            );
        }
    }


    private void downloadWithMediaStore(
            String url,
            String userAgent,
            String fileName,
            String mimeType
    ) {

        /*
         * Android 10+ 的 DownloadManager
         * 同样可以使用公共 Download 目录。
         *
         * 这里保留原有行为。
         */

        try {

            ServerStorage.downloadDirectory(fileName);

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            if (mimeType != null
                    && !mimeType.isEmpty()) {

                request.setMimeType(
                        mimeType
                );
            }

            request.setTitle(
                    fileName
            );

            request.setDescription(
                    "Pearl SR 下载"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setAllowedOverMetered(
                    true
            );

            request.setAllowedOverRoaming(
                    true
            );

            if (userAgent != null
                    && !userAgent.isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS
                            + File.separator
                            + ServerStorage.downloadFolder(fileName),
                    fileName
            );

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {

                manager.enqueue(
                        request
                );

                showToast(
                        "开始下载：" + fileName
                );
            }

        } catch (Throwable e) {

            e.printStackTrace();

            showToast(
                    "下载失败：" +
                            safeMessage(e)
            );
        }
    }


    // ============================================================
    // Blob Bridge
    // ============================================================

    private class BlobDownloadBridge {

        private OutputStream blobOutputStream;

        private Uri blobMediaStoreUri;

        private long blobExpectedSize = -1;

        private long blobWrittenSize = 0;

        private String blobFileName;

        private String blobMimeType;

        private boolean blobStarted = false;


        // --------------------------------------------------------
        // Begin
        // --------------------------------------------------------

        @android.webkit.JavascriptInterface
        public synchronized boolean beginBlob(
                String fileName,
                String mimeType,
                long expectedSize
        ) {

            try {

                cleanupBlobDownload();

                blobFileName =
                        sanitizeFileName(
                                fileName
                        );

                blobMimeType =
                        mimeType == null
                                || mimeType
                                .trim()
                                .isEmpty()
                                ? "application/octet-stream"
                                : mimeType;

                blobExpectedSize =
                        expectedSize;

                blobWrittenSize = 0;

                blobStarted = false;


                // ====================================================
                // Android 10+
                // ====================================================

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
                                    + File.separator
                                    + ServerStorage.downloadFolder(blobFileName)
                    );

                    values.put(
                            MediaStore.Downloads.IS_PENDING,
                            1
                    );

                    Uri collection =
                            MediaStore.Downloads
                                    .EXTERNAL_CONTENT_URI;

                    blobMediaStoreUri =
                            getContentResolver()
                                    .insert(
                                            collection,
                                            values
                                    );

                    if (blobMediaStoreUri == null) {

                        throw new IOException(
                                "创建 MediaStore 文件失败"
                        );
                    }

                    blobOutputStream =
                            getContentResolver()
                                    .openOutputStream(
                                            blobMediaStoreUri
                                    );

                } else {

                    // =================================================
                    // Android 9 以下
                    // =================================================

                    File downloads =
                            Environment
                                    .getExternalStoragePublicDirectory(
                                            Environment
                                                    .DIRECTORY_DOWNLOADS
                                    );

                    File folder =
                            new File(
                                    downloads,
                                    ServerStorage.downloadFolder(blobFileName)
                            );

                    if (!folder.exists()
                            && !folder.mkdirs()) {

                        throw new IOException(
                                "无法创建下载目录"
                        );
                    }

                    File file =
                            new File(
                                    folder,
                                    blobFileName
                            );

                    blobOutputStream =
                            new FileOutputStream(
                                    file
                            );
                }


                if (blobOutputStream == null) {

                    throw new IOException(
                            "无法打开输出流"
                    );
                }


                /*
                 * 非常重要：
                 *
                 * beginBlob 必须在这里同步完成，
                 * 然后 JS 才开始发送数据。
                 */

                blobStarted = true;

                showToast(
                        "开始下载：" +
                                blobFileName
                );

                return true;

            } catch (Throwable e) {

                e.printStackTrace();

                cleanupBlobDownload();

                showToast(
                        "Blob 下载失败：" +
                                safeMessage(e)
                );

                return false;
            }
        }


        // --------------------------------------------------------
        // Chunk
        // --------------------------------------------------------

        @android.webkit.JavascriptInterface
        public synchronized boolean writeBlobChunk(
                String base64
        ) {

            if (!blobStarted
                    || blobOutputStream == null) {

                return false;
            }

            try {

                byte[] data =
                        android.util.Base64.decode(
                                base64,
                                android.util.Base64.DEFAULT
                        );

                blobOutputStream.write(
                        data
                );

                blobWrittenSize +=
                        data.length;

                return true;

            } catch (Throwable e) {

                e.printStackTrace();

                abortBlob(
                        safeMessage(e)
                );

                return false;
            }
        }


        // --------------------------------------------------------
        // Finish
        // --------------------------------------------------------

        @android.webkit.JavascriptInterface
        public synchronized void finishBlob() {

            if (!blobStarted) {
                return;
            }

            try {

                if (blobOutputStream != null) {

                    blobOutputStream.flush();

                    blobOutputStream.close();

                    blobOutputStream = null;
                }


                /*
                 * 检查文件大小。
                 */

                if (blobExpectedSize >= 0
                        && blobWrittenSize !=
                        blobExpectedSize) {

                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.Q) {

                        if (blobMediaStoreUri != null) {

                            getContentResolver()
                                    .delete(
                                            blobMediaStoreUri,
                                            null,
                                            null
                                    );
                        }
                    }

                    cleanupBlobDownload();

                    showToast(
                            "Blob 下载失败："
                                    + "文件大小不完整"
                    );

                    return;
                }


                /*
                 * Android 10+
                 *
                 * 解除 IS_PENDING。
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

                        getContentResolver()
                                .update(
                                        blobMediaStoreUri,
                                        values,
                                        null,
                                        null
                                );
                    }
                }


                String finishedName =
                        blobFileName;

                long finishedSize =
                        blobWrittenSize;


                cleanupBlobDownload();


                showToast(
                        "下载完成："
                                + finishedName
                                + "\n"
                                + formatSize(
                                finishedSize
                        )
                );

            } catch (Throwable e) {

                e.printStackTrace();

                abortBlob(
                        safeMessage(e)
                );
            }
        }


        // --------------------------------------------------------
        // Abort
        // --------------------------------------------------------

        @android.webkit.JavascriptInterface
        public synchronized void abortBlob(
                String reason
        ) {

            try {

                if (blobOutputStream != null) {

                    try {

                        blobOutputStream.close();

                    } catch (Throwable ignored) {
                    }

                    blobOutputStream = null;
                }


                if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q) {

                    if (blobMediaStoreUri != null) {

                        try {

                            getContentResolver()
                                    .delete(
                                            blobMediaStoreUri,
                                            null,
                                            null
                                    );

                        } catch (Throwable ignored) {
                        }
                    }
                }


                cleanupBlobDownload();


                String message =
                        reason == null
                                ? "未知错误"
                                : reason;

                showToast(
                        "Blob 下载失败：" +
                                message
                );

            } catch (Throwable e) {

                e.printStackTrace();

                cleanupBlobDownload();
            }
        }


        // --------------------------------------------------------
        // Cleanup
        // --------------------------------------------------------

        private synchronized void
        cleanupBlobDownload() {

            try {

                if (blobOutputStream != null) {

                    try {

                        blobOutputStream.close();

                    } catch (Throwable ignored) {
                    }

                    blobOutputStream = null;
                }

            } catch (Throwable ignored) {
            }

            blobMediaStoreUri = null;

            blobExpectedSize = -1;

            blobWrittenSize = 0;

            blobFileName = null;

            blobMimeType = null;

            blobStarted = false;
        }
    }


    // ============================================================
    // Filename
    // ============================================================

    private String guessFileName(
            String url,
            String contentDisposition,
            String mimeType
    ) {

        String fileName = null;


        // --------------------------------------------------------
        // Content-Disposition
        // --------------------------------------------------------

        try {

            if (contentDisposition != null) {

                String lower =
                        contentDisposition
                                .toLowerCase(
                                        Locale.ROOT
                                );

                int index =
                        lower.indexOf(
                                "filename="
                        );

                if (index >= 0) {

                    fileName =
                            contentDisposition
                                    .substring(
                                            index + 9
                                    )
                                    .trim();

                    fileName =
                            fileName.replace(
                                    "\"",
                                    ""
                            );

                    fileName =
                            fileName.replace(
                                    "'",
                                    ""
                            );
                }
            }

        } catch (Throwable ignored) {
        }


        // --------------------------------------------------------
        // URL
        // --------------------------------------------------------

        if (fileName == null
                || fileName.isEmpty()) {

            try {

                Uri uri =
                        Uri.parse(url);

                String path =
                        uri.getPath();

                if (path != null) {

                    int slash =
                            path.lastIndexOf(
                                    '/'
                            );

                    if (slash >= 0) {

                        path =
                                path.substring(
                                        slash + 1
                                );
                    }

                    if (!path.isEmpty()) {

                        fileName = path;
                    }
                }

            } catch (Throwable ignored) {
            }
        }


        if (fileName == null
                || fileName.isEmpty()) {

            fileName =
                    "download.bin";
        }


        return sanitizeFileName(
                fileName
        );
    }


    private String sanitizeFileName(
            String name
    ) {

        if (name == null
                || name.trim().isEmpty()) {

            return "download.bin";
        }

        name =
                name.trim();

        name =
                name.replace(
                        "/",
                        "_"
                );

        name =
                name.replace(
                        "\\",
                        "_"
                );

        name =
                name.replace(
                        ":",
                        "_"
                );

        name =
                name.replace(
                        "*",
                        "_"
                );

        name =
                name.replace(
                        "?",
                        "_"
                );

        name =
                name.replace(
                        "\"",
                        "_"
                );

        name =
                name.replace(
                        "<",
                        "_"
                );

        name =
                name.replace(
                        ">",
                        "_"
                );

        name =
                name.replace(
                        "|",
                        "_"
                );

        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {

            name =
                    "download.bin";
        }

        return name;
    }


    // ============================================================
    // Utility
    // ============================================================

    private String formatSize(
            long size
    ) {

        if (size < 1024) {

            return size + " B";
        }

        if (size < 1024L * 1024L) {

            return String.format(
                    Locale.US,
                    "%.2f KB",
                    size / 1024.0
            );
        }

        if (size < 1024L *
                1024L *
                1024L) {

            return String.format(
                    Locale.US,
                    "%.2f MB",
                    size /
                            (1024.0 *
                                    1024.0)
            );
        }

        return String.format(
                Locale.US,
                "%.2f GB",
                size /
                        (1024.0 *
                                1024.0 *
                                1024.0)
        );
    }


    private String safeMessage(
            Throwable e
    ) {

        if (e == null) {

            return "未知错误";
        }

        String message =
                e.getMessage();

        if (message == null
                || message.isEmpty()) {

            return e.getClass()
                    .getSimpleName();
        }

        return message;
    }


    /**
     * Java String -> JS String
     */
    private String jsString(
            String value
    ) {

        if (value == null) {

            return "null";
        }

        StringBuilder sb =
                new StringBuilder();

        sb.append('"');

        for (int i = 0;
             i < value.length();
             i++) {

            char c =
                    value.charAt(i);

            switch (c) {

                case '\\':
                    sb.append("\\\\");
                    break;

                case '"':
                    sb.append("\\\"");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                default:
                    sb.append(c);
                    break;
            }
        }

        sb.append('"');

        return sb.toString();
    }


    // ============================================================
    // UI
    // ============================================================

    private void showToast(
            String message
    ) {

        mainHandler.post(
                () -> {

                    if (isFinishing()) {
                        return;
                    }

                    Toast.makeText(
                            SRToolsActivity.this,
                            message,
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }


    private void showProgress(
            boolean show
    ) {

        mainHandler.post(
                () -> {

                    if (progressBar == null) {
                        return;
                    }

                    progressBar.setVisibility(
                            show
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }
        );
    }


    // ============================================================
    // Back
    // ============================================================

    @Override
    public void onBackPressed() {

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

            return;
        }

        super.onBackPressed();
    }


    // ============================================================
    // Destroy
    // ============================================================

    @Override
    protected void onDestroy() {

        try {

            if (webView != null) {

                webView.stopLoading();

                webView.setWebChromeClient(
                        null
                );

                webView.setWebViewClient(
                        null
                );

                webView.destroy();

                webView = null;
            }

        } catch (Throwable e) {

            e.printStackTrace();
        }

        super.onDestroy();
    }
}
