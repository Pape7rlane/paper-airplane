package com.reversedrooms.pearlserver;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class SRToolsActivity extends Activity {

    // ============================================================
    // SRTools
    // ============================================================

    private static final String SRTOOLS_URL =
            "https://srtools.neonteam.dev/";

    // 自动保存目录：
    // Android 10+ -> Download/Pearl SR
    // Android 9-  -> Download/Pearl SR
    private static final String DOWNLOAD_FOLDER =
            "Pearl SR";

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int WRITE_REQUEST = 1002;

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
    // Blob download
    // ============================================================

    private OutputStream blobOutputStream;

    private File blobTempFile;

    private Uri blobMediaStoreUri;

    private String blobFileName;

    private String blobMimeType;

    // ============================================================
    // Activity
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_srtools);

        // --------------------------------------------------------
        // Views
        // --------------------------------------------------------

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        reloadButton = findViewById(R.id.reloadButton);
        closeButton = findViewById(R.id.closeButton);
        orientationButton = findViewById(R.id.orientationButton);

        // --------------------------------------------------------
        // System UI
        // --------------------------------------------------------

        applyFullscreenForOrientation();

        // --------------------------------------------------------
        // WebView
        // --------------------------------------------------------

        setupWebView();

        // --------------------------------------------------------
        // Buttons
        // --------------------------------------------------------

        setupButtons();

        // --------------------------------------------------------
        // Android 9 及以下权限
        // --------------------------------------------------------

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

            if (checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        WRITE_REQUEST
                );
            }
        }

        // --------------------------------------------------------
        // Load
        // --------------------------------------------------------

        webView.loadUrl(SRTOOLS_URL);
    }

    // ============================================================
    // Buttons
    // ============================================================

    private void setupButtons() {

        // 刷新
        if (reloadButton != null) {

            reloadButton.setOnClickListener(v -> {

                if (webView != null) {
                    webView.reload();
                }

            });
        }

        // 关闭
        if (closeButton != null) {

            closeButton.setOnClickListener(v -> finish());

        }

        // 横竖屏
        if (orientationButton != null) {

            orientationButton.setOnClickListener(v -> {

                boolean landscape =
                        getResources()
                                .getConfiguration()
                                .orientation
                                == Configuration.ORIENTATION_LANDSCAPE;

                if (landscape) {

                    // 横屏 -> 竖屏

                    setRequestedOrientation(
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    );

                } else {

                    // 竖屏 -> 横屏

                    setRequestedOrientation(
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    );
                }

            });
        }

        updateOrientationButton();
    }

    // ============================================================
    // 横竖屏 UI
    // ============================================================

    private void applyFullscreenForOrientation() {

        boolean landscape =
                getResources()
                        .getConfiguration()
                        .orientation
                        == Configuration.ORIENTATION_LANDSCAPE;

        Window window = getWindow();

        // --------------------------------------------------------
        // Android 11+
        // --------------------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                if (landscape) {

                    // 横屏：
                    // 隐藏状态栏
                    // 隐藏导航栏
                    // WebView 铺满整个屏幕

                    controller.hide(
                            WindowInsets.Type.statusBars()
                                    | WindowInsets.Type.navigationBars()
                    );

                    controller.setSystemBarsBehavior(
                            WindowInsetsController
                                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );

                } else {

                    // 竖屏：
                    // 恢复系统栏

                    controller.show(
                            WindowInsets.Type.statusBars()
                                    | WindowInsets.Type.navigationBars()
                    );
                }
            }

        } else {

            // ----------------------------------------------------
            // Android 10 及以下
            // ----------------------------------------------------

            if (landscape) {

                window.getDecorView().setSystemUiVisibility(

                        View.SYSTEM_UI_FLAG_FULLSCREEN

                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );

            } else {

                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        }

        updateOrientationButton();
    }

    // ============================================================
    // 横竖屏按钮文字
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

            // 当前横屏
            // 点击后切换竖屏

            orientationButton.setText("竖");

            orientationButton.setContentDescription(
                    "切换到竖屏"
            );

        } else {

            // 当前竖屏
            // 点击后切换横屏

            orientationButton.setText("横");

            orientationButton.setContentDescription(
                    "切换到横屏"
            );
        }
    }

    // ============================================================
    // 横竖屏变化
    // ============================================================

    @Override
    public void onConfigurationChanged(
            Configuration newConfig
    ) {

        super.onConfigurationChanged(newConfig);

        applyFullscreenForOrientation();

        updateOrientationButton();
    }

    // ============================================================
    // WebView
    // ============================================================

    private void setupWebView() {

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
        // 缩放
        // --------------------------------------------------------

        settings.setSupportZoom(true);

        settings.setBuiltInZoomControls(true);

        settings.setDisplayZoomControls(false);

        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(false);

        // --------------------------------------------------------
        // File / Content
        // --------------------------------------------------------

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        settings.setSupportMultipleWindows(false);

        // --------------------------------------------------------
        // HTTPS -> HTTP
        // --------------------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        // --------------------------------------------------------
        // Cookie
        // --------------------------------------------------------

        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);

        cookieManager.setAcceptThirdPartyCookies(
                webView,
                true
        );

        // --------------------------------------------------------
        // Blob Java Bridge
        // --------------------------------------------------------

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
                            android.graphics.Bitmap favicon
                    ) {

                        if (progressBar != null) {
                            progressBar.setVisibility(
                                    View.VISIBLE
                            );
                        }

                        // 尽早注入 Blob Hook
                        injectBlobInterceptor(view);
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

                        // 网页缩放
                        injectZoomFix(view);

                        // Blob 下载
                        injectBlobInterceptor(view);
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

                        // 关闭旧 callback

                        if (filePathCallback != null) {

                            filePathCallback
                                    .onReceiveValue(null);
                        }

                        filePathCallback = callback;

                        Intent intent;

                        try {

                            intent =
                                    params.createIntent();

                        } catch (Exception e) {

                            intent = new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType("*/*");
                        }

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        try {

                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER_REQUEST
                            );

                        } catch (ActivityNotFoundException e) {

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
        // 下载监听
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

                        if (url == null ||
                                url.trim().isEmpty()) {

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

                        if (url.startsWith("blob:")) {

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
    // WebView viewport
    // ============================================================

    private void injectZoomFix(WebView view) {

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
                        + "'width=device-width,"
                        + "initial-scale=1.0,"
                        + "minimum-scale=0.25,"
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
    // Blob Hook
    // ============================================================

    private void injectBlobInterceptor(
            WebView view
    ) {

        String javascript =

                "(function(){"

                        + "try{"

                        // ------------------------------------------------
                        // 防止重复安装
                        // ------------------------------------------------

                        + "if(window.__pearlBlobHookInstalled)"
                        + "return;"

                        + "window.__pearlBlobHookInstalled=true;"

                        + "window.__pearlBlobMap=new Map();"

                        + "window.__pearlLastDownloadName='';"

                        // ------------------------------------------------
                        // 记录 <a download="xxx">
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

                        // ------------------------------------------------
                        // URL.createObjectURL
                        // ------------------------------------------------

                        + "var oldCreate="
                        + "URL.createObjectURL;"

                        + "var oldRevoke="
                        + "URL.revokeObjectURL;"

                        + "URL.createObjectURL=function(obj){"

                        + "var u="
                        + "oldCreate.call(URL,obj);"

                        + "try{"

                        + "window.__pearlBlobMap.set("
                        + "u,obj"
                        + ");"

                        + "}catch(e){}"

                        + "return u;"

                        + "};"

                        // ------------------------------------------------
                        // revoke
                        // ------------------------------------------------

                        + "URL.revokeObjectURL="
                        + "function(u){"

                        + "try{"

                        + "window.__pearlBlobMap.delete(u);"

                        + "}catch(e){}"

                        + "return oldRevoke.call(URL,u);"

                        + "};"

                        // =================================================
                        // 真正下载 Blob
                        // =================================================

                        + "window.__pearlDownloadBlob="
                        + "function(url,mimeType){"

                        + "try{"

                        // ------------------------------------------------
                        // 找 Blob
                        // ------------------------------------------------

                        + "var blob="
                        + "window.__pearlBlobMap.get(url);"

                        // ------------------------------------------------
                        // 如果 Map 没有，再扫描 a[download]
                        // ------------------------------------------------

                        + "if(!blob){"

                        + "var links="
                        + "document.querySelectorAll('a[download]');"

                        + "for(var i=0;i<links.length;i++){"

                        + "if(links[i].href===url){"

                        + "if(links[i].download){"

                        + "window.__pearlLastDownloadName="
                        + "links[i].download;"

                        + "}"

                        + "break;"

                        + "}"

                        + "}"

                        + "}"

                        // ------------------------------------------------
                        // Blob 不存在
                        // ------------------------------------------------

                        + "if(!blob){"

                        + "console.error("
                        + "'Pearl SR: Blob not found',"
                        + "url"
                        + ");"

                        + "AndroidBlob.abortBlob("
                        + "'找不到 Blob 对象'"
                        + ");"

                        + "return;"

                        + "}"

                        // ------------------------------------------------
                        // 文件名
                        // ------------------------------------------------

                        + "var fileName="
                        + "window.__pearlLastDownloadName||'';"

                        + "if(!fileName){"

                        + "var links2="
                        + "document.querySelectorAll('a[download]');"

                        + "for(var j=0;j<links2.length;j++){"

                        + "if(links2[j].href===url && "
                        + "links2[j].download){"

                        + "fileName="
                        + "links2[j].download;"

                        + "break;"

                        + "}"

                        + "}"

                        + "}"

                        // ------------------------------------------------
                        // 最终默认
                        // ------------------------------------------------

                        + "if(!fileName){"

                        + "fileName='download.bin';"

                        + "}"

                        // ------------------------------------------------
                        // Java begin
                        // ------------------------------------------------

                        + "AndroidBlob.beginBlob("
                        + "fileName,"
                        + "mimeType||blob.type||"
                        + "'application/octet-stream',"
                        + "blob.size"
                        + ");"

                        // ------------------------------------------------
                        // Blob stream
                        // ------------------------------------------------

                        + "var reader="
                        + "blob.stream().getReader();"

                        + "function read(){"

                        + "return reader.read().then("
                        + "function(x){"

                        + "if(x.done){"

                        + "AndroidBlob.finishBlob();"

                        + "return;"

                        + "}"

                        + "var bytes=x.value;"

                        + "var binary='';"

                        + "var step=32768;"

                        + "for(var k=0;"
                        + "k<bytes.length;"
                        + "k+=step){"

                        + "var part="
                        + "bytes.subarray("
                        + "k,"
                        + "Math.min(k+step,bytes.length)"
                        + ");"

                        + "binary+="
                        + "String.fromCharCode.apply("
                        + "null,part"
                        + ");"

                        + "}"

                        + "var b64="
                        + "btoa(binary);"

                        + "AndroidBlob.receiveBlobChunk("
                        + "b64"
                        + ");"

                        + "return read();"

                        + "});"

                        + "}"

                        + "read();"

                        + "}catch(e){"

                        + "console.error("
                        + "'Pearl SR Blob error',"
                        + "e"
                        + ");"

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

        if (fileName == null ||
                fileName.trim().isEmpty()) {

            fileName = "download.bin";
        }

        fileName =
                sanitizeFileName(fileName);

        // ========================================================
        // Android 10+
        // ========================================================

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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
                            MediaStore.Downloads
                                    .EXTERNAL_CONTENT_URI,
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

            android.app.DownloadManager.Request request =
                    new android.app.DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setTitle(fileName);

            request.setNotificationVisibility(
                    android.app.DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationUri(uri);

            try {

                android.app.DownloadManager dm =
                        (android.app.DownloadManager)
                                getSystemService(
                                        DOWNLOAD_SERVICE
                                );

                dm.enqueue(request);

                ContentValues done =
                        new ContentValues();

                done.put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                );

                getContentResolver().update(
                        uri,
                        done,
                        null,
                        null
                );

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
                                        Environment
                                                .DIRECTORY_DOWNLOADS
                                ),
                        DOWNLOAD_FOLDER
                );

        if (!dir.exists() &&
                !dir.mkdirs()) {

            Toast.makeText(
                    this,
                    "无法创建下载目录",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        android.app.DownloadManager.Request request =
                new android.app.DownloadManager.Request(
                        Uri.parse(url)
                );

        request.setTitle(fileName);

        request.setNotificationVisibility(
                android.app.DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );

        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                DOWNLOAD_FOLDER
                        + "/"
                        + fileName
        );

        try {

            android.app.DownloadManager dm =
                    (android.app.DownloadManager)
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
    // 文件名解析
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

                // filename=
                int p =
                        lower.indexOf(
                                "filename="
                        );

                if (p >= 0) {

                    String value =
                            contentDisposition
                                    .substring(p + 9)
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

                // filename*=
                p =
                        lower.indexOf(
                                "filename*="
                        );

                if (p >= 0) {

                    String value =
                            contentDisposition
                                    .substring(p + 10)
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
            }

            // URL 文件名

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
    // 文件名清理
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
    // JS 字符串
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
    // 创建 Android 9- Blob 文件
    // ============================================================

    private Uri createLegacyBlobFile(
            String fileName
    ) {

        File dir =
                new File(
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment
                                                .DIRECTORY_DOWNLOADS
                                ),
                        DOWNLOAD_FOLDER
                );

        if (!dir.exists() &&
                !dir.mkdirs()) {

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

        blobTempFile =
                file;

        return Uri.fromFile(file);
    }

    // ============================================================
    // Android 10+ Blob MediaStore
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
                MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI,
                values
        );
    }

    // ============================================================
    // 清理 Blob
    // ============================================================

    private synchronized void cleanupBlobDownload() {

        try {

            if (blobOutputStream != null) {

                blobOutputStream.close();
            }

        } catch (Exception ignored) {
        }

        blobOutputStream =
                null;

        // MediaStore

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

        blobMediaStoreUri =
                null;

        // Android 9-

        if (blobTempFile != null &&
                blobTempFile.exists()) {

            //noinspection ResultOfMethodCallIgnored
            blobTempFile.delete();
        }

        blobTempFile =
                null;

        blobFileName =
                null;

        blobMimeType =
                null;
    }

    // ============================================================
    // Blob Bridge
    // ============================================================

    private class BlobDownloadBridge {

        // --------------------------------------------------------
        // 开始
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

            try {

                // Android 10+

                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.Q) {

                    blobMediaStoreUri =
                            beginModernBlobFile(
                                    blobFileName,
                                    blobMimeType
                            );

                    if (blobMediaStoreUri == null) {

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

                    // Android 9-

                    Uri uri =
                            createLegacyBlobFile(
                                    blobFileName
                            );

                    if (uri == null ||
                            blobTempFile == null) {

                        throw new Exception(
                                "无法创建下载文件"
                        );
                    }

                    blobOutputStream =
                            new FileOutputStream(
                                    blobTempFile
                            );
                }

                if (blobOutputStream == null) {

                    throw new Exception(
                            "下载文件没有打开"
                    );
                }

            } catch (Exception e) {

                cleanupBlobDownload();

                final String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                runOnUiThread(() ->
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
        // 接收数据
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void receiveBlobChunk(
                String base64
        ) {

            if (blobOutputStream == null) {
                return;
            }

            try {

                byte[] data;

                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O) {

                    data =
                            Base64.getDecoder()
                                    .decode(base64);

                } else {

                    data =
                            android.util.Base64.decode(
                                    base64,
                                    android.util.Base64.DEFAULT
                            );
                }

                blobOutputStream.write(data);

            } catch (Exception e) {

                final String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                cleanupBlobDownload();

                runOnUiThread(() ->
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
        // 完成
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void finishBlob() {

            if (blobOutputStream == null) {
                return;
            }

            final String completedFileName =
                    blobFileName == null
                            ? "download.bin"
                            : blobFileName;

            try {

                blobOutputStream.flush();

                blobOutputStream.close();

                blobOutputStream =
                        null;

                // Android 10+
                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.Q) {

                    if (blobMediaStoreUri != null) {

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

                blobMediaStoreUri =
                        null;

                blobTempFile =
                        null;

                blobFileName =
                        null;

                blobMimeType =
                        null;

                runOnUiThread(() ->
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

                final String message =
                        e.getMessage() == null
                                ? "未知错误"
                                : e.getMessage();

                runOnUiThread(() ->
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
        // 失败
        // --------------------------------------------------------

        @JavascriptInterface
        public synchronized void abortBlob(
                String reason
        ) {

            cleanupBlobDownload();

            runOnUiThread(() ->
                    Toast.makeText(
                            SRToolsActivity.this,
                            "Blob 下载失败："
                                    + reason,
                            Toast.LENGTH_LONG
                    ).show()
            );
        }
    }

    // ============================================================
    // 文件选择器返回
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

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        Uri[] results =
                null;

        if (resultCode == Activity.RESULT_OK &&
                data != null) {

            // 多选

            if (data.getClipData() != null) {

                int count =
                        data.getClipData()
                                .getItemCount();

                results =
                        new Uri[count];

                for (int i = 0;
                     i < count;
                     i++) {

                    results[i] =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();
                }

            }

            // 单选

            else if (data.getData() != null) {

                results =
                        new Uri[]{
                                data.getData()
                        };
            }
        }

        if (filePathCallback != null) {

            filePathCallback
                    .onReceiveValue(results);

            filePathCallback =
                    null;
        }
    }

    // ============================================================
    // 返回键
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
    // Destroy
    // ============================================================

    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {

            filePathCallback
                    .onReceiveValue(null);

            filePathCallback =
                    null;
        }

        cleanupBlobDownload();

        if (webView != null) {

            webView.stopLoading();

            webView.loadUrl(
                    "about:blank"
            );

            webView.clearHistory();

            webView.removeAllViews();

            webView.destroy();

            webView =
                    null;
        }

        super.onDestroy();
    }
}