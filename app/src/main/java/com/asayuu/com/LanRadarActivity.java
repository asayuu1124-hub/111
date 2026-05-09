package com.asayuu.com;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// 【核心修复】：更正了物理引用的包名，并显式导入引擎的内部核心类
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class LanRadarActivity extends Activity {

    private TextView tvLog;
    private XiaoyuServer server;
    private ExecutorService threadPool;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 动态构建底层 UI，杜绝 XML 依赖
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EDEDED"));
        root.setPadding(40, 40, 40, 40);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📡 局域网声呐与 Web 中枢");
        tvTitle.setTextColor(Color.parseColor("#333333"));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        root.addView(tvTitle);

        Button btnStartServer = new Button(this);
        btnStartServer.setText("启 动 物 理 文 件 互 传 (Web Server)");
        btnStartServer.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnStartServer.setTextColor(Color.parseColor("#27AE60"));
        LinearLayout.LayoutParams lpServer = new LinearLayout.LayoutParams(-1, dpToPx(55));
        lpServer.topMargin = dpToPx(30);
        root.addView(btnStartServer, lpServer);

        Button btnScan = new Button(this);
        btnScan.setText("雷 达 扫 描 局 域 网 存 活 设 备");
        btnScan.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnScan.setTextColor(Color.parseColor("#E67E22"));
        LinearLayout.LayoutParams lpScan = new LinearLayout.LayoutParams(-1, dpToPx(55));
        lpScan.topMargin = dpToPx(15);
        root.addView(btnScan, lpScan);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, -1);
        lpScroll.topMargin = dpToPx(20);
        scroll.setBackgroundResource(R.drawable.nm_card_inset);
        
        tvLog = new TextView(this);
        tvLog.setPadding(30, 30, 30, 30);
        tvLog.setTextColor(Color.parseColor("#555555"));
        tvLog.setTextSize(12f);
        tvLog.setText("终端待命...\n");
        scroll.addView(tvLog);
        
        root.addView(scroll, lpScroll);
        setContentView(root);

        // 强行锁死为 8 个物理级并发探针，执行绝对防御 OOM 策略
        threadPool = Executors.newFixedThreadPool(8);

        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleServer();
            }
        });

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!isScanning) startRadarScan();
                else Toast.makeText(LanRadarActivity.this, "雷达正在运行中...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleServer() {
        if (server == null) {
            try {
                server = new XiaoyuServer(8080);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                String ip = getLocalIpAddress();
                appendLog("✅ Web 中枢已启动。\n请在同 Wi-Fi 下的电脑浏览器访问:\nhttp://" + ip + ":8080");
            } catch (Exception e) {
                appendLog("❌ 端口 8080 被占用或权限受限: " + e.getMessage());
                server = null;
            }
        } else {
            server.stop();
            server = null;
            appendLog("🛑 Web 中枢已物理切断。");
        }
    }

    private void startRadarScan() {
        String ip = getLocalIpAddress();
        if (ip.equals("未连接 Wi-Fi")) {
            appendLog("❌ 错误：请先连接至局域网 (Wi-Fi)。");
            return;
        }
        isScanning = true;
        appendLog("🚀 启动 ICMP 声呐阵列，网段: " + ip.substring(0, ip.lastIndexOf('.')) + ".x");

        final String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
        for (int i = 1; i <= 254; i++) {
            final int suffix = i;
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress target = InetAddress.getByName(prefix + suffix);
                        if (target.isReachable(800)) { 
                            final String hostName = target.getCanonicalHostName();
                            mainHandler.post(new Runnable() {
                                @Override public void run() {
                                    appendLog("🎯 发现存活设备: " + prefix + suffix + " [" + hostName + "]");
                                }
                            });
                        }
                    } catch (Exception e) {}
                    
                    if (suffix == 254) {
                        mainHandler.post(new Runnable() {
                            @Override public void run() {
                                isScanning = false;
                                appendLog("🏁 声呐扫描完毕。");
                            }
                        });
                    }
                }
            });
        }
    }

    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            if (ipInt != 0) {
                return (ipInt & 0xFF) + "." + ((ipInt >> 8) & 0xFF) + "." + ((ipInt >> 16) & 0xFF) + "." + ((ipInt >> 24) & 0xFF);
            }
        }
        return "未连接 Wi-Fi";
    }

    private void appendLog(String msg) {
        tvLog.append(msg + "\n");
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        if (threadPool != null) threadPool.shutdownNow();
        super.onDestroy();
    }

    // --- 盲收敛 AES 加密引擎 ---
    private void encryptFileBlindly(File inFile, File outFile) throws Exception {
        String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        if (deviceId == null) deviceId = "xiaoyu_fallback_id";
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest((deviceId + "_xiaoyu_blind_drop").getBytes("UTF-8"));
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(iv);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);
        
        FileInputStream fis = new FileInputStream(inFile);
        byte[] b = new byte[8192];
        int d;
        while ((d = fis.read(b)) != -1) {
            cos.write(b, 0, d);
        }
        cos.flush();
        cos.close();
        fis.close();
    }

    // --- NanoHTTPD 微型引擎 ---
    private class XiaoyuServer extends NanoHTTPD {
        public XiaoyuServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            if (Method.POST.equals(method)) {
                try {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    String tempFilePath = files.get("uploadFile");
                    
                    if (tempFilePath != null) {
                        File tempFile = new File(tempFilePath);
                        if (tempFile.exists()) {
                            File dropDir = new File(Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault/WebDrop");
                            if (!dropDir.exists()) dropDir.mkdirs();
                            
                            String originalName = session.getParms().get("uploadFile");
                            if (originalName == null || originalName.trim().isEmpty()) {
                                originalName = "WebDrop_Push_" + System.currentTimeMillis();
                            }
                            
                            File outFile = new File(dropDir, originalName + ".snt");
                            encryptFileBlindly(tempFile, outFile);
                            
                            mainHandler.post(new Runnable() {
                                @Override public void run() {
                                    appendLog("📥 盲收敛: 收到远端文件，已加密落盘至暗盒 WebDrop 目录。");
                                }
                            });
                            
                            StringBuilder res = new StringBuilder("<html><head><meta charset='utf-8'><title>上传成功</title></head>");
                            res.append("<body style='font-family:sans-serif; padding:20px; background:#f4f4f4;'>");
                            res.append("<h2 style='color:#27AE60;'>✅ 物理穿透推送成功</h2>");
                            res.append("<p>文件已被局域网探针拦截并加密收敛至宿主机暗盒。</p>");
                            res.append("<a href='/'>[ 返回控制台 ]</a>");
                            res.append("</body></html>");
                            return newFixedLengthResponse(Response.Status.OK, "text/html", res.toString());
                        }
                    }
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "上传失败或物理拦截异常: " + e.getMessage());
                }
            }

            File exportDir = new File(Environment.getExternalStorageDirectory(), "Download/XiaoyuExport");
            if (!exportDir.exists()) exportDir.mkdirs();

            String uri = session.getUri();
            if (uri.equals("/")) {
                StringBuilder html = new StringBuilder("<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'><title>小欲数据中枢</title></head>");
                html.append("<body style='font-family:sans-serif; padding:20px; background:#f4f4f4;'>");
                html.append("<h2 style='color:#333;'>📁 物理导出舱 (XiaoyuExport)</h2><hr>");
                File[] filesArr = exportDir.listFiles();
                if (filesArr != null && filesArr.length > 0) {
                    html.append("<ul>");
                    for (File f : filesArr) {
                        if (f.isFile()) {
                            html.append("<li style='margin-bottom:10px;'><a href='/download?file=").append(f.getName()).append("' style='color:#2980B9; text-decoration:none;'>").append(f.getName()).append("</a></li>");
                        }
                    }
                    html.append("</ul>");
                } else {
                    html.append("<p style='color:#888;'>当前没有任何已解密导出的文件。</p>");
                }

                html.append("<br><br><h2 style='color:#333;'>📤 全双工盲收敛推送 (自动加密落入暗盒)</h2><hr>");
                html.append("<form method='POST' enctype='multipart/form-data' action='/'>");
                html.append("<input type='file' name='uploadFile' style='margin-bottom:15px;'><br>");
                html.append("<input type='submit' value=' 执 行 物 理 推 送 ' style='padding:10px 20px; background:#4A90E2; color:white; border:none; border-radius:5px; font-weight:bold;'>");
                html.append("</form>");

                html.append("</body></html>");
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            } else if (uri.startsWith("/download")) {
                String fileName = session.getParms().get("file");
                if (fileName != null) {
                    File target = new File(exportDir, fileName);
                    if (target.exists() && target.isFile()) {
                        try {
                            FileInputStream fis = new FileInputStream(target);
                            return newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                        } catch (FileNotFoundException e) {
                            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                        }
                    }
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 协议拒绝");
        }
    }
}
