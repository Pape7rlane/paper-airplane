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
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.Locale;

/**
 * Pearl SR - SRTools
 *
 * 主要功能：
 * 1. WebView 打开 SRTools
 * 2. 普通 HTTP/HTTPS 下载
 * 3. Blob 下载
 * 4. config.json / freesr-data.json 等 JSON 下载
 * 5. Blob 分块传输到 Android
 * 6. Android 10+ MediaStore
 * 7. Android 9 以下 Download/Pearl SR
 * 8. 文件选择器
 * 9. 横竖屏切换
 * 10. 刷新
 * 11. 关闭
 * 12. 全屏
 * 13. WebView 缩放
 */
public class SRToolsActivity extends Activity {

    private static final String TAG = "PearlSR-SRTools";

    private static final String SRTOOLS_URL =
            "https://srtools.neonteam.dev/";

    private static final String DOWNLOAD_FOLDER =
            "Pearl SR";

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

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    /*
     * ============================================================
     * Activity
     * ============================================================
     */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_srtools);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        orientationButton = findViewById(R.id.orientationButton);
        reloadButton = findViewById(R.id.reloadButton);
        closeButton = findViewById(R.id.closeButton);

        setupButtons();
        setupWebView();

        webView.loadUrl(SRTOOLS_URL);
    }

    /*
     * ============================================================
     * Buttons
     * ============================================================
     */

    private void setupButtons() {

        if (orientationButton != null) {
            orientationButton.setOnClickListener(v ->
                    toggleOrientation()
            );
        }

        if (reloadButton != null) {
            reloadButton.setOnClickListener(v -> {
                if (webView != null) {
                    webView.reload();
                }
            });
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v ->
                    finish()
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
                landscape ? "竖" : "横"
        );
    }

    /*
     * ============================================================
     * WebView
     * ============================================================
     */

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        settings.setSupportMultipleWindows(true);

        settings.setBuiltInZoomControls(true);

        settings.setDisplayZoomControls(false);

        settings.setSupportZoom(true);

        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(true);

        settings.setTextZoom(100);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        /*
         * Desktop Chrome UA
         */
        String desktopUA =
                "Mozilla/5.0 (X11; Linux x86_64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Safari/537.36";

        settings.setUserAgentString(desktopUA);

        /*
         * Cookies
         */
        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }

        /*
         * Blob JS Bridge
         *
         * 必须在 Document Start Hook 前注册。
         */
        webView.addJavascriptInterface(
                new BlobDownloadBridge(),
                "AndroidBlob"
        );

        /*
         * 安装 Document Start Blob Hook
         */
        installDocumentStartBlobHook();

        /*
         * WebViewClient
         */
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                super.onPageStarted(view, url, favicon);

                showProgress(true);

                /*
                 * 如果当前 WebView 不支持 Document Start JavaScript，
                 * 退回到页面开始时注入。
                 */
                if (!supportsDocumentStartJavaScript()) {
                    injectBlobHook(view);
                }
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);

                showProgress(false);

                /*
                 * 某些 WebView / ROM 对 Document Start 支持不完整，
                 * 页面完成后再补一次。
                 */
                if (!supportsDocumentStartJavaScript()) {
                    injectBlobHook(view);
                }

                injectViewport(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                Uri uri = request.getUrl();

                if (uri == null) {
                    return false;
                }

                String scheme =
                        uri.getScheme();

                if (scheme == null) {
                    return false;
                }

                /*
                 * blob / http / https 都交给 WebView。
                 */
                if ("blob".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme)
                        || "https".equalsIgnoreCase(scheme)) {

                    return false;
                }

                /*
                 * 其他 scheme 尝试交给系统。
                 */
                try {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    uri
                            );

                    startActivity(intent);

                    return true;

                } catch (ActivityNotFoundException e) {

                    Toast.makeText(
                            SRToolsActivity.this,
                            "无法打开链接",
                            Toast.LENGTH_SHORT
                    ).show();

                    return true;
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return super.shouldInterceptRequest(
                        view,
                        request
                );
            }
        });

        /*
         * WebChromeClient
         */
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

                        if (newProgress >= 100) {
                            showProgress(false);
                        } else {
                            showProgress(true);
                        }
                    }

                    @Override
                    public boolean onJsAlert(
                            WebView view,
                            String url,
                            String message,
                            JsResult result
                    ) {

                        /*
                         * 保持默认行为。
                         */
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
                            ValueCallback<Uri[]> filePathCallback,
                            FileChooserParams fileChooserParams
                    ) {

                        if (SRToolsActivity.this.filePathCallback
                                != null) {

                            SRToolsActivity.this.filePathCallback
                                    .onReceiveValue(null);
                        }

                        SRToolsActivity.this.filePathCallback =
                                filePathCallback;

                        openFileChooser(
                                fileChooserParams
                        );

                        return true;
                    }
                }
        );

        /*
         * DownloadListener
         *
         * HTTP/HTTPS：
         *   DownloadManager
         *
         * Blob：
         *   JS Blob Hook
         *   作为最后 fallback
         */
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

                        /*
                         * Blob 下载
                         */
                        if (url.startsWith("blob:")) {

                            String safeMime =
                                    mimeType;

                            if (safeMime == null
                                    || safeMime.trim().isEmpty()) {

                                safeMime =
                                        "application/octet-stream";
                            }

                            String js =
                                    "javascript:(function(){"
                                    + "try{"
                                    + "if(window.__pearlDownloadBlob){"
                                    + "window.__pearlDownloadBlob("
                                    + JSONObjectEscape(url)
                                    + ","
                                    + JSONObjectEscape(safeMime)
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

                        /*
                         * 普通 HTTP / HTTPS
                         */
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

                        Toast.makeText(
                                SRToolsActivity.this,
                                "不支持的下载地址",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    /*
     * ============================================================
     * Document Start Blob Hook
     *
     * 这是本次修复的核心。
     * ============================================================
     */

    private static final String DOCUMENT_START_BLOB_SCRIPT =

            "(function(){"

            + "if(window.__pearlBlobHookInstalled){return;}"
            + "window.__pearlBlobHookInstalled=true;"

            /*
             * Blob URL -> Blob 映射
             */
            + "window.__pearlBlobMap=new Map();"

            /*
             * 防止重复下载
             */
            + "window.__pearlBlobActive=new Set();"

            /*
             * 安全转义辅助
             */
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

            /*
             * 保存 Blob
             */
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

            /*
             * 不再因为 revokeObjectURL 立刻删除 Map。
             *
             * 这是重要修复：
             * 有些网页会在 a.click() 后立即 revoke，
             * 如果这里删除 Map，Android DownloadListener
             * 稍后收到 blob URL 时就找不到 Blob。
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

            /*
             * 真正发送 Blob 给 Android
             */
            + "window.__pearlSendBlob=function(blob,name,mime){"

            + "try{"

            + "name=window.__pearlBlobSafeName(name);"

            + "mime=mime||blob.type||'application/octet-stream';"

            + "if(!window.AndroidBlob){"
            + "throw new Error('AndroidBlob接口不存在');"
            + "}"

            /*
             * beginBlob 必须同步调用。
             */
            + "AndroidBlob.beginBlob(name,mime,blob.size);"

            /*
             * 优先 Blob.stream()
             */
            + "if(blob.stream){"

            + "var reader=blob.stream().getReader();"

            + "var readNext=function(){"

            + "reader.read().then(function(r){"

            + "if(r.done){"
            + "AndroidBlob.finishBlob();"
            + "return;"
            + "}"

            + "var bytes=new Uint8Array(r.value);"

            + "var chunkSize=32768;"

            + "for(var i=0;i<bytes.length;i+=chunkSize){"

            + "var end=Math.min(i+chunkSize,bytes.length);"

            + "var part=bytes.slice(i,end);"

            + "var binary='';"

            + "for(var j=0;j<part.length;j++){"
            + "binary+=String.fromCharCode(part[j]);"
            + "}"

            + "AndroidBlob.writeBlobChunk("
            + "btoa(binary)"
            + ");"

            + "}"

            + "readNext();"

            + "}).catch(function(e){"

            + "try{"
            + "AndroidBlob.abortBlob(String(e));"
            + "}catch(x){}"

            + "});"

            + "};"

            + "readNext();"

            + "return;"
            + "}"

            /*
             * FileReader fallback
             */
            + "var fr=new FileReader();"

            + "fr.onload=function(){"

            + "try{"

            + "var data=fr.result||'';"

            + "var comma=data.indexOf(',');"

            + "if(comma>=0){"
            + "data=data.substring(comma+1);"
            + "}"

            + "var chunk=32768;"

            + "for(var i=0;i<data.length;i+=chunk){"
            + "AndroidBlob.writeBlobChunk("
            + "data.substring(i,Math.min(i+chunk,data.length))"
            + ");"
            + "}"

            + "AndroidBlob.finishBlob();"

            + "}catch(e){"

            + "try{"
            + "AndroidBlob.abortBlob(String(e));"
            + "}catch(x){}"

            + "}"

            + "};"

            + "fr.onerror=function(){"
            + "try{"
            + "AndroidBlob.abortBlob('FileReader失败');"
            + "}catch(e){}"
            + "};"

            + "fr.readAsDataURL(blob);"

            + "}catch(e){"

            + "try{"
            + "AndroidBlob.abortBlob(String(e));"
            + "}catch(x){}"

            + "}"

            + "};"

            /*
             * Blob URL 下载
             *
             * 第一优先级：
             * Map 中直接拿 Blob
             *
             * 第二优先级：
             * fetch(blobURL)
             *
             * 这样即使 Blob 是由 Worker 创建的，
             * 只要主页面拿到了 blob URL，也能直接 fetch。
             */
            + "window.__pearlDownloadBlob=function(url,mime,name){"

            + "try{"

            + "if(!url||String(url).indexOf('blob:')!==0){"
            + "throw new Error('不是Blob URL');"
            + "}"

            + "url=String(url);"

            /*
             * 防止同一 Blob 被 click hook +
             * DownloadListener 重复处理。
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
             * 从 Map 获取
             */
            + "var mapped=null;"

            + "try{"
            + "mapped=window.__pearlBlobMap.get(url);"
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
             * Map 没找到，直接 fetch Blob URL。
             */
            + "fetch(url).then(function(resp){"

            + "if(!resp.ok){"
            + "throw new Error('Blob fetch失败: '+resp.status);"
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
            + "AndroidBlob.abortBlob(String(e));"
            + "}catch(x){}"

            + "}"

            + "};"

            /*
             * 处理 <a href="blob:..." download="...">
             */
            + "window.__pearlHandleBlobAnchor=function(a){"

            + "try{"

            + "if(!a){return false;}"

            + "var href=a.href||a.getAttribute('href')||'';"

            + "if(!href||String(href).indexOf('blob:')!==0){"
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

            /*
             * 捕获阶段监听用户点击。
             *
             * 这是核心修复之一：
             * 不再等 DownloadListener 才处理。
             */
            + "document.addEventListener('click',function(ev){"

            + "try{"

            + "var node=ev.target;"

            + "while(node&&node!==document){"

            + "if(node.tagName&&"
            + "node.tagName.toLowerCase()==='a'){"

            + "var href=node.href||"
            + "node.getAttribute('href')||'';"

            + "if(String(href).indexOf('blob:')===0){"

            + "window.__pearlHandleBlobAnchor(node);"

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

            /*
             * 拦截程序主动 a.click()
             *
             * 很多网页下载 JSON 的方式就是：
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

            + "var href=this.href||"
            + "this.getAttribute('href')||'';"

            + "if(String(href).indexOf('blob:')===0){"

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

            + "return oldAnchorClick.apply(this,arguments);"

            + "};"

            + "}catch(e){}"

            /*
             * 记录页面中的 Blob anchor。
             */
            + "document.addEventListener('mousedown',function(ev){"

            + "try{"

            + "var node=ev.target;"

            + "while(node&&node!==document){"

            + "if(node.tagName&&"
            + "node.tagName.toLowerCase()==='a'){"

            + "var href=node.href||"
            + "node.getAttribute('href')||'';"

            + "if(String(href).indexOf('blob:')===0){"
            + "return;"
            + "}"

            + "break;"
            + "}"

            + "node=node.parentElement;"

            + "}"

            + "}catch(e){}"

            + "},true);"

            + "})();";

    /*
     * ============================================================
     * Document Start 安装
     * ============================================================
     */

    private void installDocumentStartBlobHook() {

        if (!supportsDocumentStartJavaScript()) {
            return;
        }

        try {

            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    DOCUMENT_START_BLOB_SCRIPT,
                    java.util.Collections.singleton(
                            Uri.parse("https://srtools.neonteam.dev")
                    )
            );

        } catch (Throwable e) {

            /*
             * 某些旧 WebView / ROM 可能抛异常。
             * 后面会使用 fallback injectBlobHook。
             */
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

    private void injectBlobHook(WebView view) {

        try {

            view.evaluateJavascript(
                    DOCUMENT_START_BLOB_SCRIPT,
                    null
            );

        } catch (Throwable e) {

            e.printStackTrace();
        }
    }

    /*
     * ============================================================
     * Viewport
     * ============================================================
     */

    private void injectViewport(WebView view) {

        String js =

                "(function(){"

                + "try{"

                + "var meta=document.querySelector("
                + "'meta[name=\"viewport\"]'"
                + ");"

                + "if(!meta){"

                + "meta=document.createElement('meta');"

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

    /*
     * ============================================================
     * 文件选择器
     * ============================================================
     */

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

                intent.setType("*/*");
            }

            startActivityForResult(
                    intent,
                    FILE_CHOOSER_REQUEST
            );

        } catch (ActivityNotFoundException e) {

            filePathCallback = null;

            Toast.makeText(
                    this,
                    "没有可用的文件选择器",
                    Toast.LENGTH_SHORT
            ).show();
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

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        if (resultCode == RESULT_OK
                && data != null) {

            Uri uri = data.getData();

            if (uri != null) {
                results = new Uri[]{uri};
            }
        }

        filePathCallback.onReceiveValue(results);

        filePathCallback = null;
    }

    /*
     * ============================================================
     * HTTP / HTTPS 下载
     * ============================================================
     */

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
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
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

            Toast.makeText(
                    this,
                    "下载失败：" + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String pendingHttpUrl;
    private String pendingHttpUserAgent;
    private String pendingHttpFileName;
    private String pendingHttpMimeType;

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

        if (requestCode != WRITE_PERMISSION_REQUEST) {
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

            Toast.makeText(
                    this,
                    "没有存储权限，无法保存文件",
                    Toast.LENGTH_SHORT
            ).show();
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

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            if (mimeType != null
                    && !mimeType.isEmpty()) {

                request.setMimeType(mimeType);
            }

            request.setTitle(fileName);

            request.setDescription(
                    "Pearl SR 下载"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setAllowedOverMetered(true);

            request.setAllowedOverRoaming(true);

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
                            + DOWNLOAD_FOLDER,
                    fileName
            );

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {

                manager.enqueue(request);

                Toast.makeText(
                        this,
                        "开始下载：" + fileName,
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Throwable e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "下载失败：" + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void downloadWithMediaStore(
            String url,
            String userAgent,
            String fileName,
            String mimeType
    ) {

        try {

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            if (mimeType != null
                    && !mimeType.isEmpty()) {

                request.setMimeType(mimeType);
            }

            request.setTitle(fileName);

            request.setDescription(
                    "Pearl SR 下载"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setAllowedOverMetered(true);

            request.setAllowedOverRoaming(true);

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
                            + DOWNLOAD_FOLDER,
                    fileName
            );

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {

                manager.enqueue(request);

                Toast.makeText(
                        this,
                        "开始下载：" + fileName,
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Throwable e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "下载失败：" + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * ============================================================
     * Blob Download Bridge
     * ============================================================
     */

    private class BlobDownloadBridge {

        private OutputStream blobOutputStream;

        private Uri blobMediaStoreUri;

        private long blobExpectedSize = -1;

        private long blobWrittenSize = 0;

        private String blobFileName;

        private String blobMimeType;

        private boolean blobStarted = false;

        /**
         * 必须同步执行。
         */
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
                        (mimeType == null
                                || mimeType.trim().isEmpty())
                                ? "application/octet-stream"
                                : mimeType;

                blobExpectedSize =
                        expectedSize;

                blobWrittenSize = 0;

                blobStarted = false;

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
                                    + File.separator
                                    + DOWNLOAD_FOLDER
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

                    /*
                     * Android 9 以下
                     */
                    File downloads =
                            Environment
                                    .getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS
                                    );

                    File folder =
                            new File(
                                    downloads,
                                    DOWNLOAD_FOLDER
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
                 * 关键：
                 * beginBlob 完成后才返回 JS。
                 */
                blobStarted = true;

                showToast(
                        "开始下载：" + blobFileName
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

                blobOutputStream.write(data);

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
                 * 检查大小。
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

                    Uri failedUri =
                            blobMediaStoreUri;

                    cleanupBlobDownload();

                    showToast(
                            "Blob 下载失败：文件大小不完整"
                    );

                    return;
                }

                /*
                 * Android 10+
                 *
                 * IS_PENDING = 0
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
                        + formatSize(finishedSize)
                );

            } catch (Throwable e) {

                e.printStackTrace();

                abortBlob(
                        safeMessage(e)
                );
            }
        }

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
                        "Blob 下载失败："
                        + message
                );

            } catch (Throwable e) {

                e.printStackTrace();

                cleanupBlobDownload();
            }
        }

        private synchronized void cleanupBlobDownload() {

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

    /*
     * ============================================================
     * 文件名
     * ============================================================
     */

    private String guessFileName(
            String url,
            String contentDisposition,
            String mimeType
    ) {

        String fileName = null;

        try {

            if (contentDisposition != null) {

                String lower =
                        contentDisposition.toLowerCase(
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
                                            index
                                                    + 9
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

        if (fileName == null
                || fileName.isEmpty()) {

            try {

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

        /*
         * 如果服务器没有扩展名，
         * 根据 MIME 类型补扩展名。
         */
        if (!hasExtension(fileName)
                && mimeType != null
                && !mimeType.isEmpty()) {

            String extension =
                    MimeTypeMap
                            .getSingleton()
                            .getExtensionFromMimeType(
                                    mimeType
                            );

            if (extension != null
                    && !extension.isEmpty()) {

                fileName +=
                        "." + extension;
            }
        }

        return sanitizeFileName(
                fileName
        );
    }

    private boolean hasExtension(
            String fileName
    ) {

        int dot =
                fileName.lastIndexOf('.');

        return dot > 0
                && dot < fileName.length() - 1;
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

        if (name.isEmpty()) {
            name = "download.bin";
        }

        return name;
    }

    /*
     * ============================================================
     * Toast / UI
     * ============================================================
     */

    private void showToast(
            String message
    ) {

        mainHandler.post(() -> {

            if (isFinishing()) {
                return;
            }

            Toast.makeText(
                    SRToolsActivity.this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void showProgress(
            boolean show
    ) {

        mainHandler.post(() -> {

            if (progressBar == null) {
                return;
            }

            progressBar.setVisibility(
                    show
                            ? View.VISIBLE
                            : View.GONE
            );
        });
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private String formatSize(
            long size
    ) {

        if (size < 1024) {
            return size + " B";
        }

        if (size < 1024 * 1024) {

            return String.format(
                    Locale.US,
                    "%.2f KB",
                    size / 1024.0
            );
        }

        if (size < 1024L * 1024L * 1024L) {

            return String.format(
                    Locale.US,
                    "%.2f MB",
                    size / (1024.0 * 1024.0)
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
     * 简单 JS 字符串转义。
     */
    private String JSONObjectEscape(
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

    /*
     * ============================================================
     * Back / Destroy
     * ============================================================
     */

    @Override
    public void onBackPressed() {

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {

        try {

            if (webView != null) {

                webView.stopLoading();

                webView.setWebChromeClient(null);

                webView.setWebViewClient(null);

                webView.destroy();

                webView = null;
            }

        } catch (Throwable e) {

            e.printStackTrace();
        }

        super.onDestroy();
    }
}