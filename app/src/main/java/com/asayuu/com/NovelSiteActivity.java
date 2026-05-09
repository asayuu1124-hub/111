package com.asayuu.com;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;

public class NovelSiteActivity extends Activity {

    private WebView webView;
    private ProgressBar loader;
    private LinearLayout webViewLayout;
    private Button btnStartImmersive;

    private RelativeLayout layoutImmersive;
    private TextView tvReaderContent;
    private Button btnFontMinus, btnFontPlus, btnTheme, btnExitReader;
    private ScrollView scrollReader;

    private int currentTheme = 0; 
    private float currentTextSize = 18f;
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
                extractNovelContentJsoup();
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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (isMalicious(url.toLowerCase())) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (isMalicious(url.toLowerCase())) {
                    view.stopLoading();
                    return;
                }
                if (System.currentTimeMillis() - lastTouchTime > 3500) {
                    view.stopLoading();
                    if (view.canGoBack()) view.goBack();
                    return;
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.toLowerCase().startsWith("http")) return true; 
                if (isMalicious(url.toLowerCase())) {
                    view.stopLoading();
                    return true; 
                }
                if (System.currentTimeMillis() - lastTouchTime > 2500) {
                    view.stopLoading();
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

    // 核心重构点：利用 Jsoup 在后臺線程直接解析目標 DOM，徹底避開 WebView 的內存陷阱
    private void extractNovelContentJsoup() {
        final String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) return;
        
        Toast.makeText(this, "启动 Jsoup 深度提取...", Toast.LENGTH_SHORT).show();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Document doc = Jsoup.connect(currentUrl)
                            .userAgent("Mozilla/5.0 (Linux; Android 10)")
                            .timeout(10000)
                            .get();
                            
                    // 物理抹除所有干擾節點
                    doc.select("script, style, iframe, .ad, .footer, .header, nav, #adv").remove();
                    
                    // 針對中國大陸主流小說站點的特徵探測
                    Elements contentElements = doc.select("#content, #chaptercontent, #BookText, .read_chapterDetail, #nr_1, #novelcontent, .Readarea");
                    final String text = contentElements.isEmpty() ? doc.body().text() : contentElements.text();
                    
                    // 將純淨文本拋回 UI 線程
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (text.trim().isEmpty()) {
                                Toast.makeText(NovelSiteActivity.this, "未能嗅探到有效正文，或当前处于目录页", Toast.LENGTH_SHORT).show();
                            } else {
                                tvReaderContent.setText(text.trim().replaceAll("　　", "\n\n"));
                                webViewLayout.setVisibility(View.GONE);
                                btnStartImmersive.setVisibility(View.GONE);
                                layoutImmersive.setVisibility(View.VISIBLE);
                                scrollReader.scrollTo(0, 0); 
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(NovelSiteActivity.this, "Jsoup 提取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                    });
                }
            }
        }).start();
    }

    private void injectCleanerJs(WebView view) {
        String js = "javascript:(function() {" +
                    "   var style = document.createElement('style');" +
                    "   style.innerHTML = 'iframe, .footer, .bottom-ad, .header, .nav, .ad-box, #adv { display: none !important; }';" +
                    "   document.head.appendChild(style);" +
                    "})()";
        view.loadUrl(js);
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
