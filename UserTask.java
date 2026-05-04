package com.asayuu.com;

import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class UserTask {
   // 替换为你的真实 IP
private static final String API_URL = "http://38.175.195.195/user_action.php";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UserCallback {
        void onResult(boolean success, String message);
    }

    public void execute(final String action, final String user, final String pass, final UserCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = doRequest(action, user, pass);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        parseResponse(result, callback);
                    }
                });
            }
        }).start();
    }

    private String doRequest(String action, String user, String pass) {
        try {
            trustAllSSL();
            HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setRequestProperty("Content-Type", "application/json");

            // 构建 JSON 请求体
            JSONObject j = new JSONObject();
            j.put("action", action); // "login" 或 "register"
            j.put("username", user);
            j.put("password", pass);

            c.getOutputStream().write(j.toString().getBytes("UTF-8"));

            if (c.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder s = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) s.append(line);
                return s.toString();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void parseResponse(String result, UserCallback callback) {
        if (result == null) {
            callback.onResult(false, "云端连接失败，请检查网络");
            return;
        }
        try {
            JSONObject json = new JSONObject(result);
            int code = json.optInt("code");
            String msg = json.optString("msg");
            callback.onResult(code == 200, msg);
        } catch (Exception e) {
            callback.onResult(false, "云端数据解析异常");
        }
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
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String h, SSLSession s) { return true; }
            });
        } catch (Exception e) {}
    }
}