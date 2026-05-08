package com.asayuu.com;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;

public class NovelSiteActivity extends Activity {

    private WebView webView;
    private ProgressBar loader;
    private LinearLayout webViewLayout;
    private Button btnStartImmersive;

    // 沉浸阅读器组件
    private RelativeLayout layoutImmersive;
    private TextView tvReaderContent;
    private Button btnFontMinus, btnFontPlus, btnTheme, btnExitReader;
    private ScrollView scrollReader;

    private int currentTheme = 0; 
    private float currentTextSize = 18f;

    // 🛡️ 核心：记录用户最后一次触控屏幕的时间，用于判断是否为"自动跳转"
    private long lastTouchTime = System.currentTimeMillis();

    private String[] adBlacklist = {
        "union.", "ads.", "guanggao", "popunder", "poker", "casino", "bet66", 
        "sex", "porn", "xvideos", "/av/", "dubo", "caipiao", "bocai", ".apk", 
        "cnzz.", "baidu.com/cpro", "alimama", "seiqing", "sm.sm", "xpj", "macau", 
        "jinsha", "chaturbate", "live", "taobao.com", "jd.com", "pinduoduo.com", "alipay"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_novel_site);

        initViews();
        initSettings();
        initReaderControls();

        String url = getIntent().getStringExtra("target_url");
        if (url == null || url.isEmpty()) {
            url = "https://owlook.com.cn/";
        }
        webView.loadUrl(url);
    }

    private void initViews() {
        webView = (WebView) findViewById(R.id.novel_webview);
        loader = (ProgressBar) findViewById(R.id.novel_loader);
        webViewLayout = (LinearLayout) findViewById(R.id.novel_webview_container);
        btnStartImmersive = (Button) findViewById(R.id.btn_start_immersive);

        layoutImmersive = (RelativeLayout) findViewById(R.id.layout_immersive_reader);
        scrollReader = (ScrollView) findViewById(R.id.scroll_reader);
        tvReaderContent = (TextView) findViewById(R.id.tv_reader_content);
        btnFontMinus = (Button) findViewById(R.id.btn_reader_font_minus);
        btnFontPlus = (Button) findViewById(R.id.btn_reader_font_plus);
        btnTheme = (Button) findViewById(R.id.btn_reader_theme);
        btnExitReader = (Button) findViewById(R.id.btn_reader_exit);

        // 🛡️ 注入触控监听，刷新活动时间
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                    lastTouchTime = System.currentTimeMillis();
                }
                return false;
            }
        });

        btnStartImmersive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractNovelContent();
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
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile Safari/537.36)");
        
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new ReaderTool(), "ReaderTool");

        webView.setWebViewClient(new WebViewClient() {
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptResource(url);
            }

            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    return interceptResource(request.getUrl().toString());
                }
                return null;
            }

            private WebResourceResponse interceptResource(String url) {
                if (isMalicious(url.toLowerCase())) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (isMalicious(url.toLowerCase())) {
                    executeDestructiveBlock(view);
                    return;
                }

                // 🛡️ 防线一：拦截长延迟的后台暗跳 (距离上次触摸超过 3.5 秒)
                if (System.currentTimeMillis() - lastTouchTime > 3500) {
                    view.stopLoading();
                    if (view.canGoBack()) view.goBack();
                    Toast.makeText(NovelSiteActivity.this, "🛡️ 已静默拦截后台恶意暗跳", Toast.LENGTH_SHORT).show();
                    return;
                }

                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return interceptUrl(view, url);
            }

            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    return interceptUrl(view, request.getUrl().toString());
                }
                return false;
            }

            private boolean interceptUrl(WebView view, String url) {
                String u = url.toLowerCase();
                if (!u.startsWith("http")) return true; 

                if (isMalicious(u)) {
                    executeDestructiveBlock(view);
                    return true; 
                }

                // 🛡️ 防线二：拦截脚本触发的自动跳转 (距离上次触摸超过 2.5 秒)
                if (System.currentTimeMillis() - lastTouchTime > 2500) {
                    view.stopLoading();
                    Toast.makeText(NovelSiteActivity.this, "🛡️ 已拦截脚本自动跳转", Toast.LENGTH_SHORT).show();
                    return true;
                }

                return false; 
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                btnStartImmersive.setVisibility(View.VISIBLE);
                injectCleanerJs(view);
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

    private boolean isMalicious(String u) {
        for (int i = 0; i < adBlacklist.length; i++) {
            if (u.contains(adBlacklist[i])) return true;
        }
        return false;
    }

    private void executeDestructiveBlock(final WebView view) {
        view.stopLoading();
        String safeHtml = "<html><body style='background:#EDEDED; display:flex; justify-content:center; align-items:center; height:100vh; flex-direction:column; font-family:sans-serif;'>" +
                          "<h2 style='color:#E74C3C;'>🚫 已彻底摧毁跳转脚本</h2>" +
                          "<p style='color:#666; text-align:center;'>小欲已将该色情/博彩页面从内存中抹除。<br><br>请点击手机【返回键】继续阅读。</p>" +
                          "</body></html>";
        view.loadDataWithBaseURL(null, safeHtml, "text/html", "utf-8", null);
        Toast.makeText(this, "触发最高级别防护，DOM已销毁", Toast.LENGTH_SHORT).show();
    }

    private void initReaderControls() {
        btnFontPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentTextSize < 36f) { 
                    currentTextSize += 2f; 
                    tvReaderContent.setTextSize(currentTextSize); 
                }
            }
        });

        btnFontMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentTextSize > 12f) { 
                    currentTextSize -= 2f; 
                    tvReaderContent.setTextSize(currentTextSize); 
                }
            }
        });

        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                currentTheme = (currentTheme + 1) % 3;
                applyTheme();
            }
        });

        btnExitReader.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                layoutImmersive.setVisibility(View.GONE);
                webViewLayout.setVisibility(View.VISIBLE);
                btnStartImmersive.setVisibility(View.VISIBLE);
            }
        });
    }

    private void applyTheme() {
        View controlBar = findViewById(R.id.reader_controls);
        if (currentTheme == 0) {
            layoutImmersive.setBackgroundColor(Color.parseColor("#F4ECD8"));
            controlBar.setBackgroundColor(Color.parseColor("#E6DCC3"));
            tvReaderContent.setTextColor(Color.parseColor("#333333"));
            btnTheme.setText("🌙 夜间");
            btnFontMinus.setTextColor(Color.parseColor("#555555"));
            btnFontPlus.setTextColor(Color.parseColor("#555555"));
        } else if (currentTheme == 1) {
            layoutImmersive.setBackgroundColor(Color.parseColor("#1A1A1A"));
            controlBar.setBackgroundColor(Color.parseColor("#111111"));
            tvReaderContent.setTextColor(Color.parseColor("#999999"));
            btnTheme.setText("☀️ 日间");
            btnFontMinus.setTextColor(Color.parseColor("#999999"));
            btnFontPlus.setTextColor(Color.parseColor("#999999"));
        } else {
            layoutImmersive.setBackgroundColor(Color.parseColor("#FFFFFF"));
            controlBar.setBackgroundColor(Color.parseColor("#EEEEEE"));
            tvReaderContent.setTextColor(Color.parseColor("#333333"));
            btnTheme.setText("📜 护眼");
            btnFontMinus.setTextColor(Color.parseColor("#555555"));
            btnFontPlus.setTextColor(Color.parseColor("#555555"));
        }
    }

    private void extractNovelContent() {
        Toast.makeText(this, "正在嗅探正文...", Toast.LENGTH_SHORT).show();
        String js = "javascript:(function(){" +
                    "var selectors = ['#content', '#chaptercontent', '#BookText', '.read_chapterDetail', '#nr_1', '#novelcontent', '.Readarea'];" +
                    "var text = '';" +
                    "for(var i=0; i<selectors.length; i++) {" +
                    "   var el = document.querySelector(selectors[i]);" +
                    "   if(el) {" +
                    "       var html = el.innerHTML;" +
                    "       html = html.replace(/<br\\s*[\\/]?>/gi, '\\n');" +
                    "       html = html.replace(/&nbsp;/gi, ' ');" +
                    "       var tmp = document.createElement('DIV');" +
                    "       tmp.innerHTML = html;" +
                    "       text = tmp.textContent || tmp.innerText || '';" +
                    "       break;" +
                    "   }" +
                    "}" +
                    "window.ReaderTool.processContent(text);" +
                    "})()";
        webView.loadUrl(js);
    }

    // 🛡️ 终极物理销毁：不但隐藏广告，还把所有会作妖的 iframe 连根拔起
    private void injectCleanerJs(WebView view) {
        String js = "javascript:(function() {" +
                    "   var style = document.createElement('style');" +
                    "   style.innerHTML = 'iframe, .footer, .bottom-ad, .header, .nav, .ad-box, #adv { display: none !important; }';" +
                    "   document.head.appendChild(style);" +
                    "   var frames = document.getElementsByTagName('iframe');" +
                    "   for(var i=frames.length-1; i>=0; i--) { frames[i].parentNode.removeChild(frames[i]); }" +
                    "})()";
        view.loadUrl(js);
    }

    class ReaderTool {
        @JavascriptInterface
        public void processContent(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (text == null || text.trim().isEmpty()) {
                        Toast.makeText(NovelSiteActivity.this, "未能嗅探到有效正文，或当前在目录页", Toast.LENGTH_SHORT).show();
                    } else {
                        tvReaderContent.setText(text.trim());
                        webViewLayout.setVisibility(View.GONE);
                        btnStartImmersive.setVisibility(View.GONE);
                        layoutImmersive.setVisibility(View.VISIBLE);
                        scrollReader.scrollTo(0, 0); 
                    }
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (layoutImmersive.getVisibility() == View.VISIBLE) {
                btnExitReader.performClick();
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
