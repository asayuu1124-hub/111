package com.asayuu.com;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.io.ByteArrayInputStream;

public class VideoSiteActivity extends Activity {

    private WebView webView;
    private ProgressBar loader;
    private LinearLayout webViewLayout;
    
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout decorView;

    // 🛡️ 极其精简的黑名单（只拦广告域名，不搜关键字，防止误杀）
    private String[] adBlacklist = {
        "union.baidu.com", "ads.", "guanggao", "popunder", "poker", "casino", "bet66"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_video_site);

        webView = (WebView) findViewById(R.id.video_webview);
        loader = (ProgressBar) findViewById(R.id.video_loader);
        webViewLayout = (LinearLayout) findViewById(R.id.layout_webview_container);
        decorView = (FrameLayout) getWindow().getDecorView();

        // 🛠️ 核心：启动时彻底清除缓存，防止加载之前出错的源码页面
        webView.clearCache(true);
        webView.clearHistory();

        initSettings();
        
        String url = getIntent().getStringExtra("target_url");
        if (url == null || url.isEmpty()) {
            url = "https://m.v.qq.com/";
        }
        webView.loadUrl(url);

        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(VideoSiteActivity.this, "深度重载中...", Toast.LENGTH_SHORT).show();
                webView.clearCache(true);
                webView.reload();
                return true;
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
        
        // 🛠️ 核心：禁用缓存模式，强制从网络获取，解决“回退变源码”的问题
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        // 🎭 使用非常标准的移动端 UA
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36");

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebViewClient(new WebViewClient() {
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                String u = url.toLowerCase();
                
                // 🛡️ 核心：白名单极速放行
                // 只要 URL 包含网站主域名，或者包含视频/样式特征，直接返回 null（让系统正常加载）
                if (u.contains("fdzys.com") || u.contains("huazidm.com") || u.contains("skr2.cc") || 
                    u.contains(".m3u8") || u.contains(".ts") || u.contains(".mp4") || u.contains("video")) {
                    return null; 
                }

                // 只拦截纯广告黑名单
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
                // 处理所有 http/https 链接在内部打开
                if (url.startsWith("http")) {
                    view.loadUrl(url);
                    return true;
                }
                return false; 
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) loader.setVisibility(View.GONE);
                else {
                    loader.setVisibility(View.VISIBLE);
                    loader.setProgress(newProgress);
                }
            }

            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
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

    private void injectCleanerJs(WebView view) {
        String js = "javascript:(function() {" +
                    "   var style = document.createElement('style');" +
                    "   style.innerHTML = '#adv, .ad-box, .bottom-ad, .pop-ups { display: none !important; }';" +
                    "   document.head.appendChild(style);" +
                    "})()";
        view.loadUrl(js);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.getWebChromeClient().onHideCustomView();
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
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