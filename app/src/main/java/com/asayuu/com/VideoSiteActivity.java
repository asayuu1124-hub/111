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
import android.widget.Toast;
import java.io.ByteArrayInputStream;

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

        // 模块三：嗅探按钮点击事件
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
                
                // 模块三：嗅探核心逻辑
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
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(lp);
        }
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
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.destroy();
        }
        super.onDestroy();
    }
}