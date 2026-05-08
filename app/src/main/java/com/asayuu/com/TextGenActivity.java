package com.asayuu.com;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TextGenActivity extends Activity {

    private TextView tvContent;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 断网或 API 失效时的本地降级备用库
    private String[] fallbackTiangou = {
        "宝，我今天去吃面了，吃的是什么面？突然想见你一面。",
        "今天下雨了，别人等伞，而我在等你回消息。",
        "你昨天晚上没回我消息，我看了整整一晚上的聊天记录，我觉得你肯定是太累了，早点休息宝。",
        "宝，今天发工资了，一共发了3000，我给你转了3000，剩下的钱我留着吃饭，你说我懂事吗？",
        "我对你的爱就像拖拉机上山，轰轰烈烈..."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_gen);
        
        trustAllSSL(); // 绕过部分低版本 Android 的 HTTPS 证书拦截

        tvContent = (TextView) findViewById(R.id.tv_content);
        Button btnHitokoto = (Button) findViewById(R.id.btn_hitokoto);
        Button btnTiangou = (Button) findViewById(R.id.btn_tiangou);
        Button btnCopy = (Button) findViewById(R.id.btn_copy);
        Button btnBack = (Button) findViewById(R.id.btn_back);

        btnHitokoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchText("hitokoto");
            }
        });

        btnTiangou.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchText("tiangou");
            }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = tvContent.getText().toString();
                if (!text.isEmpty() && !text.equals("点击上方按钮获取灵感...") && !text.equals("正在从云端汲取灵感...")) {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("TextGen", text));
                    Toast.makeText(TextGenActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void fetchText(final String type) {
        tvContent.setText("正在从云端汲取灵感...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String result = "";
                    if (type.equals("hitokoto")) {
                        // 获取每日一言 (含动画、文学、原创等多种分类)
                        URL url = new URL("https://v1.hitokoto.cn/?c=a&c=b&c=c&c=d&c=i");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        
                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            
                            JSONObject json = new JSONObject(sb.toString());
                            String hitokoto = json.optString("hitokoto");
                            String from = json.optString("from");
                            result = hitokoto + "\n\n—— 「" + from + "」";
                        } else {
                            result = "获取失败，服务器无响应。";
                        }
                    } else if (type.equals("tiangou")) {
                        // 获取舔狗日记 (纯文本返回)
                        URL url = new URL("https://api.vvhan.com/api/text/tiangou");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        
                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            result = sb.toString();
                        } else {
                            // API 报错时触发降级机制
                            result = fallbackTiangou[new Random().nextInt(fallbackTiangou.length)];
                        }
                    }

                    final String finalResult = result;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvContent.setText(finalResult);
                        }
                    });
                } catch (Exception e) {
                    final String fallback = type.equals("tiangou") ? 
                            fallbackTiangou[new Random().nextInt(fallbackTiangou.length)] : "网络请求异常，请检查网络连接是否正常。";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvContent.setText(fallback);
                        }
                    });
                }
            }
        }).start();
    }

    private void trustAllSSL() {
        try {
            TrustManager[] tm = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, tm, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {}
    }
}
