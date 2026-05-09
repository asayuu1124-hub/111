package com.asayuu.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

public class VideoSiteActivity extends Activity {

    private WebView webView;
    private ProgressBar loader;
    private LinearLayout webViewLayout;
    private Button btnSniff;
    
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout decorView;

    private String sniffedUrl = "";
    private String[] adBlacklist = { "union.baidu.com", "ads.", "guanggao", "popunder", "poker", "casino", "bet66" };

    // 核心重构：引入绝对防御的单线程队列与全局引擎
    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_video_site);

        webView = (WebView) findViewById(R.id.video_webview);
        loader = (ProgressBar) findViewById(R.id.video_loader);
        webViewLayout = (LinearLayout) findViewById(R.id.layout_webview_container);
        decorView = (FrameLayout) getWindow().getDecorView();
        
        btnSniff = (Button) findViewById(R.id.btn_sniff_result);

        webView.clearCache(true);
        webView.clearHistory();

        initSettings();
        
        String url = getIntent().getStringExtra("target_url");
        if (url == null || url.isEmpty()) url = "https://m.v.qq.com/";
        webView.loadUrl(url);

        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                Toast.makeText(VideoSiteActivity.this, "深度重载中...", Toast.LENGTH_SHORT).show();
                webView.clearCache(true);
                webView.reload();
                return true;
            }
        });

        btnSniff.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!sniffedUrl.isEmpty()) showSniffDialog(sniffedUrl);
            }
        });
    }

    private void initSettings() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36");

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                String u = url.toLowerCase();
                
                if (u.contains(".m3u8") || u.contains(".mp4")) {
                    final String detectedUrl = url;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (sniffedUrl.isEmpty() || !sniffedUrl.equals(detectedUrl)) {
                                sniffedUrl = detectedUrl;
                                btnSniff.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                    return null; 
                }

                if (u.contains("fdzys.com") || u.contains("huazidm.com") || u.contains("skr2.cc") || 
                    u.contains(".ts") || u.contains("video")) {
                    return null; 
                }

                for (int i = 0; i < adBlacklist.length; i++) {
                    if (u.contains(adBlacklist[i])) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCleanerJs(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http")) { view.loadUrl(url); return true; }
                return false; 
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) loader.setVisibility(View.GONE);
                else { loader.setVisibility(View.VISIBLE); loader.setProgress(newProgress); }
            }

            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                webViewLayout.setVisibility(View.GONE);
                decorView.addView(customView, new FrameLayout.LayoutParams(-1, -1));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                decorView.removeView(customView);
                customView = null;
                webViewLayout.setVisibility(View.VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            }
        });
    }

    private void showSniffDialog(final String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✅ 发现无广告视频直链");
        builder.setMessage("地址：\n" + url);
        builder.setPositiveButton("外部播放", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(url), "video/*");
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(VideoSiteActivity.this, "未找到支持的播放器", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("复制链接", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("video_url", url));
                Toast.makeText(VideoSiteActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 核心重构：注入物理级加密下载引擎入口
        if (url.contains(".m3u8")) {
            builder.setNeutralButton("物理加密落盘", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    executeM3U8Download(url);
                }
            });
        }
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(lp);
        }
    }

    // --- 底层 M3U8 解析与流式加密组装引擎 ---
    private void executeM3U8Download(final String url) {
        final android.app.Dialog loadingDialog = new android.app.Dialog(this);
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setCancelable(false);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"));
        ProgressBar pb = new ProgressBar(this);
        layout.addView(pb);
        final TextView tvProgress = new TextView(this);
        tvProgress.setText("解析 M3U8 索引中...");
        tvProgress.setTextColor(android.graphics.Color.parseColor("#333333"));
        tvProgress.setPadding(0, 30, 0, 0);
        layout.addView(tvProgress);
        loadingDialog.setContentView(layout);
        if (loadingDialog.getWindow() != null) loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        loadingDialog.show();

        singleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String targetM3u8 = url;
                    String m3u8Content = fetchText(targetM3u8);
                    
                    // 递归解析主播放列表
                    if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                        String[] lines = m3u8Content.split("\n");
                        for (String line : lines) {
                            if (!line.startsWith("#") && !line.trim().isEmpty()) {
                                if (line.startsWith("http")) targetM3u8 = line;
                                else if (line.startsWith("/")) {
                                    java.net.URL u = new java.net.URL(url);
                                    targetM3u8 = u.getProtocol() + "://" + u.getHost() + line;
                                } else {
                                    targetM3u8 = url.substring(0, url.lastIndexOf('/') + 1) + line;
                                }
                                break;
                            }
                        }
                        m3u8Content = fetchText(targetM3u8);
                    }

                    List<String> tsUrls = new ArrayList<String>();
                    String baseUrl = targetM3u8.substring(0, targetM3u8.lastIndexOf('/') + 1);
                    String[] lines = m3u8Content.split("\n");
                    for (String line : lines) {
                        if (!line.startsWith("#") && !line.trim().isEmpty()) {
                            if (line.startsWith("http")) tsUrls.add(line);
                            else if (line.startsWith("/")) {
                                java.net.URL u = new java.net.URL(targetM3u8);
                                tsUrls.add(u.getProtocol() + "://" + u.getHost() + line);
                            } else tsUrls.add(baseUrl + line);
                        }
                    }

                    if (tsUrls.isEmpty()) throw new Exception("未能解析到任何 TS 碎片");

                    final int totalTs = tsUrls.size();
                    runOnUiThread(new Runnable() {
                        @Override public void run() { tvProgress.setText("准备开始物理级流式加密落盘...\n共 " + totalTs + " 个分片"); }
                    });

                    File baseDir = new File(android.os.Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault/Video");
                    if (!baseDir.exists()) baseDir.mkdirs();
                    File outFile = new File(baseDir, "SafeVideo_" + System.currentTimeMillis() + ".snt");

                    String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                    if (deviceId == null) deviceId = "xiaoyu_fallback_id";
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] key = digest.digest((deviceId + "_xiaoyu_video_master_key").getBytes("UTF-8"));
                    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    byte[] iv = new byte[16];
                    new SecureRandom().nextBytes(iv);
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(iv);
                    CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                    Sink sink = Okio.sink(cos);
                    BufferedSink bufferedSink = Okio.buffer(sink);

                    // 内存直驱拼接
                    for (int i = 0; i < tsUrls.size(); i++) {
                        final int current = i + 1;
                        runOnUiThread(new Runnable() {
                            @Override public void run() { tvProgress.setText("正在执行绝对防御流下载...\n[" + current + " / " + totalTs + "] 分片加密中"); }
                        });

                        Request request = new Request.Builder().url(tsUrls.get(i)).build();
                        Response response = okHttpClient.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            BufferedSource source = Okio.buffer(response.body().source());
                            bufferedSink.writeAll(source);
                            source.close();
                        }
                        response.close();
                    }

                    bufferedSink.flush();
                    bufferedSink.close();
                    cos.close();
                    fos.close();

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loadingDialog.dismiss();
                            Toast.makeText(VideoSiteActivity.this, "✅ M3U8 嗅探与碎片组装落盘成功！\n已存入暗盒 Video 目录。", Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loadingDialog.dismiss();
                            Toast.makeText(VideoSiteActivity.this, "❌ 提取失败或遭遇 OOM 防御阻断:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private String fetchText(String url) throws Exception {
        Request request = new Request.Builder().url(url).build();
        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String res = response.body().string();
            response.close();
            return res;
        }
        throw new Exception("HTTP 请求失败: 状态异常");
    }

    private void injectCleanerJs(WebView view) {
        String js = "javascript:(function() {" +
                    "   var style = document.createElement('style');" +
                    "   style.innerHTML = '#adv, .ad-box, .bottom-ad, .pop-ups { display: none !important; }';" +
                    "   document.head.appendChild(style);" +
                    "})()";
        view.loadUrl(js);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) { super.onConfigurationChanged(newConfig); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) { webView.getWebChromeClient().onHideCustomView(); return true; }
            if (webView.canGoBack()) { webView.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (singleThreadExecutor != null) {
            singleThreadExecutor.shutdownNow();
        }
        if (webViewLayout != null && webView != null) {
            webViewLayout.removeView(webView);
        }
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.destroy();
        }
        super.onDestroy();
    }
}
