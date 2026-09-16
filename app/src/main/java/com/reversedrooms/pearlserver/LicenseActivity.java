package com.reversedrooms.pearlserver;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LicenseActivity extends Activity {

    /*
     * ============================================================
     * GitHub 卡密配置
     * ============================================================
     *
     * 每次启动都会从这里重新获取最新 licenses.json。
     *
     * 注意：
     * 不要改成本地文件。
     */

    private static final String LICENSE_URL =
            "https://raw.githubusercontent.com/Pape7rlane/paper-airplane/main/licenses.json";


    /*
     * ============================================================
     * SharedPreferences
     * ============================================================
     *
     * PREF_KEY
     * 保存已经验证成功的卡密 SHA-256。
     *
     * PREF_CONFIG_HASH
     * 保存当时验证成功的 licenses.json SHA-256。
     *
     * 只要 licenses.json 内容发生任何变化，
     * PREF_CONFIG_HASH 就会和服务器上的 Hash 不一致。
     *
     * 此时：
     *
     * 旧卡密立即失效
     * ↓
     * 删除本地验证信息
     * ↓
     * 要求重新输入卡密
     */

    private static final String PREFS =
            "pearl_license";

    private static final String PREF_KEY =
            "verified_key_hash";

    private static final String PREF_CONFIG_HASH =
            "verified_config_hash";


    /*
     * ============================================================
     * UI
     * ============================================================
     */

    private EditText keyInput;

    private TextView statusText;

    private TextView verifyButton;

    private ProgressBar progress;


    /*
     * ============================================================
     * 后台线程
     * ============================================================
     */

    private final ExecutorService io =
            Executors.newSingleThreadExecutor();


    /*
     * ============================================================
     * Activity 创建
     * ============================================================
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        /*
         * 卡密页面
         */

        setContentView(
                R.layout.activity_license
        );


        /*
         * 获取 UI
         */

        keyInput =
                findViewById(
                        R.id.licenseKeyInput
                );

        statusText =
                findViewById(
                        R.id.licenseStatus
                );

        verifyButton =
                findViewById(
                        R.id.licenseVerifyButton
                );

        progress =
                findViewById(
                        R.id.licenseProgress
                );


        /*
         * ========================================================
         * 初始状态
         * ========================================================
         *
         * 在 GitHub 验证完成之前：
         *
         * 输入框关闭
         * 验证按钮关闭
         * 显示加载状态
         *
         * 防止用户在网络检查完成之前操作。
         */

        keyInput.setEnabled(false);

        verifyButton.setEnabled(false);

        progress.setVisibility(
                View.VISIBLE
        );

        statusText.setText(
                "卡密验证 / License Verification\n\n" +
                "正在连接 GitHub 验证服务器…"
        );


        /*
         * ========================================================
         * 验证按钮
         * ========================================================
         */

        verifyButton.setOnClickListener(
                v -> verifyKey()
        );


        /*
         * ========================================================
         * 每次启动强制检查
         * ========================================================
         *
         * 这里不能读取本地卡密后直接进入 MainActivity。
         *
         * 必须：
         *
         * App 启动
         * ↓
         * GitHub
         * ↓
         * 下载 licenses.json
         * ↓
         * 计算配置 Hash
         * ↓
         * 验证卡密
         */

        checkVerificationServer();
    }


    /*
     * ============================================================
     * 每次启动检查 GitHub
     * ============================================================
     */

    private void checkVerificationServer() {

        io.execute(() -> {

            String json;

            try {

                /*
                 * =================================================
                 * 强制获取最新 licenses.json
                 * =================================================
                 */

                json =
                        downloadLicenseFile();


                /*
                 * =================================================
                 * 检查 JSON
                 * =================================================
                 */

                JSONObject root =
                        new JSONObject(json);


                JSONArray keys =
                        root.optJSONArray(
                                "keys"
                        );


                if (keys == null) {

                    throw new Exception(
                            "卡密配置格式错误：缺少 keys"
                    );
                }


                /*
                 * 即使 keys 是空数组，
                 * 也允许正常读取。
                 *
                 * 最终不会有任何有效卡密。
                 */

            } catch (Exception e) {

                /*
                 * =================================================
                 * GitHub 无法访问
                 * =================================================
                 *
                 * 这里非常重要：
                 *
                 * 不允许使用本地旧卡密直接进入。
                 *
                 * 不允许离线模式。
                 *
                 * 不允许缓存绕过。
                 */

                runOnUiThread(() -> {

                    progress.setVisibility(
                            View.GONE
                    );

                    keyInput.setText("");

                    keyInput.setEnabled(false);

                    verifyButton.setEnabled(false);


                    statusText.setText(
                            "无法连接验证服务器\n" +
                            "Unable to connect to verification server\n\n" +

                            "必须连接互联网并访问 GitHub 才能验证卡密。\n" +
                            "An internet connection to GitHub is required.\n\n" +

                            "请检查网络连接，必要时开启网络代理后重试。\n" +
                            "Please check your connection or enable a network proxy."
                    );
                });

                return;
            }


            /*
             * =====================================================
             * GitHub 获取成功
             * =====================================================
             */

            final String finalJson =
                    json;


            /*
             * =====================================================
             * 计算 licenses.json SHA-256
             * =====================================================
             *
             * 整个 JSON 文件参与 Hash。
             *
             * 因此：
             *
             * 改 key
             * 改 expires
             * 改 enabled
             * 增加卡密
             * 删除卡密
             * 修改格式
             * 修改空格
             * 修改换行
             *
             * 都会产生新的 Hash。
             */

            final String currentConfigHash =
                    sha256(
                            finalJson
                    );


            runOnUiThread(() -> {

                progress.setVisibility(
                        View.GONE
                );


                SharedPreferences prefs =
                        getSharedPreferences(
                                PREFS,
                                MODE_PRIVATE
                        );


                /*
                 * =================================================
                 * 获取本地保存信息
                 * =================================================
                 */

                String savedKeyHash =
                        prefs.getString(
                                PREF_KEY,
                                null
                        );


                String savedConfigHash =
                        prefs.getString(
                                PREF_CONFIG_HASH,
                                null
                        );


                /*
                 * =================================================
                 * 没有保存卡密
                 * =================================================
                 */

                if (
                        savedKeyHash == null
                                ||
                        savedKeyHash.isEmpty()
                ) {

                    enableInput();

                    statusText.setText(
                            "请输入卡密\n" +
                            "Enter License Key"
                    );

                    return;
                }


                /*
                 * =================================================
                 * 检查 licenses.json 是否发生变化
                 * =================================================
                 */

                boolean configChanged =
                        savedConfigHash == null
                                ||
                        !constantTimeEquals(
                                savedConfigHash,
                                currentConfigHash
                        );


                if (configChanged) {

                    /*
                     * =================================================
                     * 配置发生变化
                     * =================================================
                     *
                     * 旧卡密全部重新验证。
                     */

                    clearLocalLicense();


                    keyInput.setText("");

                    enableInput();


                    statusText.setText(
                            "卡密配置已更新\n" +
                            "License configuration updated\n\n" +

                            "检测到服务器卡密配置发生变化。\n" +
                            "The license configuration has changed.\n\n" +

                            "原来的卡密已失效，请重新输入新的卡密。\n" +
                            "Your previous license is no longer valid.\n\n" +

                            "请输入新的卡密。\n" +
                            "Please enter a new license."
                    );


                    return;
                }


                /*
                 * =================================================
                 * 配置没有变化
                 * =================================================
                 *
                 * 但是仍然不能直接进入。
                 *
                 * 必须使用刚刚从 GitHub 下载的 JSON
                 * 再验证一次保存的卡密。
                 */

                keyInput.setEnabled(false);

                verifyButton.setEnabled(false);

                progress.setVisibility(
                        View.VISIBLE
                );


                statusText.setText(
                        "正在在线验证已保存的卡密…\n" +
                        "Checking saved license online…"
                );


                verifySavedHash(
                        savedKeyHash,
                        finalJson,
                        currentConfigHash
                );
            });
        });
    }


    /*
     * ============================================================
     * 用户输入卡密
     * ============================================================
     */

    private void verifyKey() {

        String key =
                keyInput
                        .getText()
                        .toString()
                        .trim();


        /*
         * 空卡密
         */

        if (key.isEmpty()) {

            statusText.setText(
                    "请输入卡密\n" +
                    "Enter License Key"
            );

            return;
        }


        /*
         * 禁止重复点击
         */

        verifyButton.setEnabled(false);

        keyInput.setEnabled(false);

        progress.setVisibility(
                View.VISIBLE
        );


        statusText.setText(
                "正在在线验证…\n" +
                "Verifying online…"
        );


        /*
         * 卡密 Hash
         */

        final String hash =
                sha256(
                        key
                );


        /*
         * 再次从 GitHub 获取最新配置。
         *
         * 这样即使用户停留在页面期间，
         * GitHub 上的 licenses.json 被修改，
         * 也会使用最新版本进行验证。
         */

        fetchAndCheck(
                hash,
                true
        );
    }


    /*
     * ============================================================
     * 验证已经保存的卡密
     * ============================================================
     */

    private void verifySavedHash(
            String hash,
            String json,
            String configHash
    ) {

        io.execute(() -> {

            String result;

            try {

                /*
                 * 使用刚刚从 GitHub 下载的 JSON。
                 */

                result =
                        checkLicense(
                                json,
                                hash
                        );

            } catch (Exception e) {

                result =
                        "卡密验证失败\n" +
                        "License verification failed";
            }


            final String finalResult =
                    result;


            runOnUiThread(() -> {

                progress.setVisibility(
                        View.GONE
                );


                /*
                 * =================================================
                 * 卡密有效
                 * =================================================
                 */

                if (
                        finalResult.startsWith(
                                "OK|"
                        )
                ) {

                    String expires =
                            finalResult.substring(
                                    3
                            );


                    String expiryText =
                            buildExpiryText(
                                    expires
                            );


                    statusText.setText(
                            "卡密验证成功\n" +
                            "License Verified\n\n" +
                            expiryText
                    );


                    /*
                     * 进入 MainActivity。
                     *
                     * 此时满足：
                     *
                     * 1. GitHub 可访问
                     * 2. licenses.json 有效
                     * 3. 配置 Hash 没变化
                     * 4. 本地卡密存在
                     * 5. 本地卡密在线验证成功
                     */

                    openMain(
                            expires
                    );

                    return;
                }


                /*
                 * =================================================
                 * 保存的卡密已经无效
                 * =================================================
                 */

                clearLocalLicense();

                keyInput.setText("");

                enableInput();


                statusText.setText(
                        "本地保存的卡密已失效\n" +
                        "Saved License Expired / Invalid\n\n" +

                        finalResult +
                        "\n\n" +

                        "请输入新的卡密。\n" +
                        "Please enter a new license."
                );
            });
        });
    }


    /*
     * ============================================================
     * 在线获取并验证用户输入的卡密
     * ============================================================
     */

    private void fetchAndCheck(
            String hash,
            boolean fromInput
    ) {

        io.execute(() -> {

            String result;

            String configHash =
                    null;


            try {

                /*
                 * =================================================
                 * 获取最新 licenses.json
                 * =================================================
                 */

                String json =
                        downloadLicenseFile();


                /*
                 * =================================================
                 * 计算配置 Hash
                 * =================================================
                 */

                configHash =
                        sha256(
                                json
                        );


                /*
                 * =================================================
                 * 在线验证卡密
                 * =================================================
                 */

                result =
                        checkLicense(
                                json,
                                hash
                        );

            } catch (Exception e) {

                /*
                 * 网络失败。
                 *
                 * 不允许进入 MainActivity。
                 */

                result =
                        "网络验证失败：无法获取最新卡密配置\n" +
                        "Network verification failed";
            }


            final String finalResult =
                    result;

            final String finalConfigHash =
                    configHash;


            runOnUiThread(() -> {

                progress.setVisibility(
                        View.GONE
                );


                /*
                 * =================================================
                 * 验证成功
                 * =================================================
                 */

                if (
                        finalResult.startsWith(
                                "OK|"
                        )
                ) {

                    String expires =
                            finalResult.substring(
                                    3
                            );


                    /*
                     * =================================================
                     * 保存验证结果
                     * =================================================
                     *
                     * 只有：
                     *
                     * GitHub 获取成功
                     * +
                     * JSON Hash 成功计算
                     * +
                     * 卡密验证成功
                     *
                     * 才保存。
                     */

                    if (
                            finalConfigHash != null
                                    &&
                            !finalConfigHash.isEmpty()
                    ) {

                        getSharedPreferences(
                                PREFS,
                                MODE_PRIVATE
                        )
                                .edit()
                                .putString(
                                        PREF_KEY,
                                        hash
                                )
                                .putString(
                                        PREF_CONFIG_HASH,
                                        finalConfigHash
                                )
                                .apply();
                    }


                    /*
                     * 显示有效期。
                     */

                    String expiryText =
                            buildExpiryText(
                                    expires
                            );


                    statusText.setText(
                            "卡密验证成功\n" +
                            "License Verified\n\n" +
                            expiryText
                    );


                    /*
                     * 进入主界面
                     */

                    openMain(
                            expires
                    );

                    return;
                }


                /*
                 * =================================================
                 * 验证失败
                 * =================================================
                 */

                /*
                 * 用户输入错误时，
                 * 本地原有卡密不能因为输入一次错误
                 * 就被删除。
                 *
                 * 只有 fromInput == false 的情况下
                 * 才清理保存的卡密。
                 */

                if (!fromInput) {

                    clearLocalLicense();
                }


                keyInput.setText("");

                enableInput();


                statusText.setText(
                        finalResult +
                        "\n\n" +
                        "请输入有效卡密。\n" +
                        "Please enter a valid license."
                );
            });
        });
    }


    /*
     * ============================================================
     * 下载最新 licenses.json
     * ============================================================
     */

    private String downloadLicenseFile()
            throws Exception {


        /*
         * ========================================================
         * 加时间戳
         * ========================================================
         *
         * 防止网络缓存。
         */

        String url =
                LICENSE_URL +
                "?t=" +
                System.currentTimeMillis();


        HttpURLConnection conn =
                (HttpURLConnection)
                        new URL(url)
                                .openConnection();


        try {

            /*
             * GET
             */

            conn.setRequestMethod(
                    "GET"
            );


            /*
             * 连接超时
             */

            conn.setConnectTimeout(
                    10000
            );


            /*
             * 读取超时
             */

            conn.setReadTimeout(
                    10000
            );


            /*
             * HTTP 请求头
             */

            conn.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            conn.setRequestProperty(
                    "Cache-Control",
                    "no-cache, no-store, max-age=0"
            );


            conn.setRequestProperty(
                    "Pragma",
                    "no-cache"
            );


            conn.setRequestProperty(
                    "User-Agent",
                    "PearlServerAndroid-License/4.5.54"
            );


            /*
             * =====================================================
             * 获取 HTTP 状态码
             * =====================================================
             */

            int code =
                    conn.getResponseCode();


            if (
                    code != HttpURLConnection.HTTP_OK
            ) {

                throw new Exception(
                        "HTTP " + code
                );
            }


            /*
             * =====================================================
             * 读取服务器返回内容
             * =====================================================
             */

            try (
                    InputStream in =
                            conn.getInputStream();

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            in,
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                StringBuilder sb =
                        new StringBuilder();


                String line;


                while (
                        (line =
                                reader.readLine())
                                != null
                ) {

                    sb.append(
                            line
                    );
                }


                String result =
                        sb.toString();


                /*
                 * 空文件直接失败。
                 */

                if (
                        result.trim().isEmpty()
                ) {

                    throw new Exception(
                            "服务器返回空数据"
                    );
                }


                return result;
            }

        } finally {

            conn.disconnect();
        }
    }


    /*
     * ============================================================
     * 检查卡密
     * ============================================================
     */

    private String checkLicense(
            String json,
            String wantedHash
    ) {

        try {

            JSONObject root =
                    new JSONObject(
                            json
                    );


            JSONArray keys =
                    root.optJSONArray(
                            "keys"
                    );


            if (keys == null) {

                return
                        "卡密配置格式错误\n" +
                        "License configuration error";
            }


            /*
             * 当前日期
             */

            LocalDate today =
                    LocalDate.now();


            /*
             * =====================================================
             * 遍历所有卡密
             * =====================================================
             */

            for (
                    int i = 0;
                    i < keys.length();
                    i++
            ) {

                JSONObject item =
                        keys.optJSONObject(
                                i
                        );


                if (item == null) {

                    continue;
                }


                /*
                 * =================================================
                 * 获取服务器卡密
                 * =================================================
                 */

                String plainKey =
                        item.optString(
                                "key",
                                ""
                        ).trim();


                if (
                        plainKey.isEmpty()
                ) {

                    continue;
                }


                /*
                 * =================================================
                 * SHA-256
                 * =================================================
                 */

                String plainKeyHash =
                        sha256(
                                plainKey
                        );


                /*
                 * =================================================
                 * 常量时间比较
                 * =================================================
                 */

                if (
                        !constantTimeEquals(
                                plainKeyHash,
                                wantedHash
                        )
                ) {

                    continue;
                }


                /*
                 * =================================================
                 * 找到了对应卡密
                 * =================================================
                 */


                /*
                 * =================================================
                 * enabled
                 * =================================================
                 */

                boolean enabled =
                        item.optBoolean(
                                "enabled",
                                true
                        );


                if (!enabled) {

                    return
                            "此卡密已被禁用\n" +
                            "License Disabled";
                }


                /*
                 * =================================================
                 * expires
                 * =================================================
                 */

                String expires =
                        item.optString(
                                "expires",
                                "never"
                        ).trim();


                /*
                 * =================================================
                 * 永久卡
                 * =================================================
                 */

                if (
                        expires.isEmpty()
                                ||
                        expires.equalsIgnoreCase(
                                "never"
                        )
                ) {

                    return "OK|never";
                }


                /*
                 * =================================================
                 * 日期格式
                 * =================================================
                 */

                LocalDate expiry;

                try {

                    expiry =
                            LocalDate.parse(
                                    expires
                            );

                } catch (
                        DateTimeParseException e
                ) {

                    return
                            "卡密有效期配置错误\n" +
                            "Invalid expiration date";
                }


                /*
                 * =================================================
                 * 到期判断
                 * =================================================
                 *
                 * 到期当天仍然有效。
                 *
                 * 例如：
                 *
                 * expires = 2026-09-19
                 *
                 * 2026-09-19 -> 有效
                 * 2026-09-20 -> 过期
                 */

                if (
                        expiry.isBefore(
                                today
                        )
                ) {

                    return
                            "此卡密已过期\n" +
                            "License Expired";
                }


                /*
                 * =================================================
                 * 有效
                 * =================================================
                 */

                return
                        "OK|" +
                        expires;
            }


            /*
             * =====================================================
             * 没有找到
             * =====================================================
             */

            return
                    "卡密无效\n" +
                    "Invalid License";


        } catch (Exception e) {

            return
                    "卡密数据解析失败\n" +
                    "License Data Error";
        }
    }


    /*
     * ============================================================
     * 有效期显示
     * ============================================================
     */

    private String buildExpiryText(
            String expires
    ) {

        /*
         * 永久卡
         */

        if (
                expires == null
                        ||
                expires.trim().isEmpty()
                        ||
                expires.equalsIgnoreCase(
                        "never"
                )
        ) {

            return
                    "有效期：永久\n" +
                    "Expires: Never";
        }


        try {

            LocalDate expiry =
                    LocalDate.parse(
                            expires
                    );


            LocalDate today =
                    LocalDate.now();


            long days =
                    ChronoUnit.DAYS.between(
                            today,
                            expiry
                    );


            /*
             * 已经过期。
             */

            if (days < 0) {

                return
                        "有效期：已过期\n" +
                        "Expires: Expired";
            }


            /*
             * 今天到期。
             */

            if (days == 0) {

                return
                        "有效期至：" +
                        expires +
                        "\n" +

                        "Expires: " +
                        expires +
                        "\n\n" +

                        "今天到期\n" +
                        "Expires today";
            }


            /*
             * 正常有效期。
             */

            return
                    "有效期至：" +
                    expires +
                    "\n" +

                    "Expires: " +
                    expires +
                    "\n\n" +

                    "剩余 " +
                    days +
                    " 天\n" +

                    "Remaining: " +
                    days +
                    " days";


        } catch (
                DateTimeParseException e
        ) {

            return
                    "有效期：" +
                    expires +
                    "\n" +

                    "Expires: " +
                    expires;
        }
    }


    /*
     * ============================================================
     * SHA-256
     * ============================================================
     */

    private String sha256(
            String text
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );


            byte[] bytes =
                    digest.digest(
                            text.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            StringBuilder sb =
                    new StringBuilder(
                            bytes.length * 2
                    );


            for (
                    byte b :
                    bytes
            ) {

                sb.append(
                        String.format(
                                Locale.ROOT,
                                "%02x",
                                b
                        )
                );
            }


            return sb.toString();


        } catch (Exception e) {

            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    e
            );
        }
    }


    /*
     * ============================================================
     * 常量时间字符串比较
     * ============================================================
     */

    private boolean constantTimeEquals(
            String a,
            String b
    ) {

        if (a == null || b == null) {

            return false;
        }


        byte[] aa =
                a.toLowerCase(
                        Locale.ROOT
                )
                        .getBytes(
                                StandardCharsets.UTF_8
                        );


        byte[] bb =
                b.toLowerCase(
                        Locale.ROOT
                )
                        .getBytes(
                                StandardCharsets.UTF_8
                        );


        return MessageDigest.isEqual(
                aa,
                bb
        );
    }


    /*
     * ============================================================
     * 清除本地卡密
     * ============================================================
     */

    private void clearLocalLicense() {

        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
                .edit()
                .remove(
                        PREF_KEY
                )
                .remove(
                        PREF_CONFIG_HASH
                )
                .apply();
    }


    /*
     * ============================================================
     * 开启输入
     * ============================================================
     */

    private void enableInput() {

        keyInput.setEnabled(
                true
        );

        verifyButton.setEnabled(
                true
        );

        progress.setVisibility(
                View.GONE
        );
    }


    /*
     * ============================================================
     * 进入 MainActivity
     * ============================================================
     */

    private void openMain(
            String expires
    ) {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );


        /*
         * 把有效期传给 MainActivity。
         *
         * 目前 MainActivity 不读取也没关系。
         */

        intent.putExtra(
                "license_expires",
                expires
        );


        startActivity(
                intent
        );


        finish();
    }


    /*
     * ============================================================
     * Activity 销毁
     * ============================================================
     */

    @Override
    protected void onDestroy() {

        io.shutdownNow();

        super.onDestroy();
    }

}
