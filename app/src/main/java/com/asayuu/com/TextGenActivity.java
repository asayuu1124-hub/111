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

    // 斷網或 API 失效時的本地降級備用庫
    private String[] fallbackTiangou = {
        "寶，我今天去吃麵了，吃的是什麼麵？突然想見你一麵。",
        "今天下雨了，別人等傘，而我在等你回訊息。",
        "你昨天晚上沒回我消息，我看了整整一晚上的聊天記錄，我覺得你肯定是太累了，早點休息寶。",
        "寶，今天發薪水了，一共發了3000，我給你轉了3000，剩下的錢我留著吃飯，你說我懂事嗎？",
        "我對你的愛就像拖拉機上山，轟轟烈烈..."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_gen);
        
        trustAllSSL(); // 繞過部分低版本 Android 的 HTTPS 證書攔截

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
                if (!text.isEmpty() && !text.equals("點擊上方按鈕獲取靈感...") && !text.equals("正在從雲端汲取靈感...")) {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("TextGen", text));
                    Toast.makeText(TextGenActivity.this, "已複製到剪貼簿", Toast.LENGTH_SHORT).show();
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
        tvContent.setText("正在從雲端汲取靈感...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String result = "";
                    if (type.equals("hitokoto")) {
                        // 獲取每日一言 (含動畫、文學、原創等多種分類)
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
                            result = "獲取失敗，伺服器無響應。";
                        }
                    } else if (type.equals("tiangou")) {
                        // 獲取舔狗日記 (純文本返回)
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
                            // API 報錯時觸發降級機制
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
                            fallbackTiangou[new Random().nextInt(fallbackTiangou.length)] : "網絡請求異常，請檢查網絡連接是否正常。";
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