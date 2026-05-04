package com.asayuu.com;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.*;
import android.widget.ProgressBar;

public class NovelSiteActivity extends Activity {

    private WebView webView;
    private ProgressBar loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_novel_site);

        webView = (WebView) findViewById(R.id.novel_webview);
        loader = (ProgressBar) findViewById(R.id.novel_loader);

        initSettings();
        
        String url = getIntent().getStringExtra("target_url");
        webView.loadUrl(url == null ? "https://m.qidian.com/" : url);

        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
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
        // 🛠️ 修正：删除了报错的 setAppCacheEnabled
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false); 

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNovelCleaner(view);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
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
        });
    }

    private void injectNovelCleaner(WebView view) {
        String js = "javascript:(function() {" +
                    "   var style = document.createElement('style');" +
                    "   style.innerHTML = '.download-app, .bottom-fixed, #footer, .pop-ups { display: none !important; }';" +
                    "   document.head.appendChild(style);" +
                    "})()";
        view.loadUrl(js);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}