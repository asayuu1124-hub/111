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
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 【核心修复】：更正了物理引用的包名，并显式导入引擎的内部核心类
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
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

        threadPool = Executors.newFixedThreadPool(20); // 维持 20 个并发探针

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

    // --- NanoHTTPD 微型引擎 ---
    private class XiaoyuServer extends NanoHTTPD {
        public XiaoyuServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            File exportDir = new File(Environment.getExternalStorageDirectory(), "Download/XiaoyuExport");
            if (!exportDir.exists()) exportDir.mkdirs();

            String uri = session.getUri();
            if (uri.equals("/")) {
                StringBuilder html = new StringBuilder("<html><head><meta charset='utf-8'><title>小欲数据中枢</title></head>");
                html.append("<body style='font-family:sans-serif; padding:20px; background:#f4f4f4;'>");
                html.append("<h2 style='color:#333;'>📁 物理导出舱 (XiaoyuExport)</h2><hr>");
                File[] files = exportDir.listFiles();
                if (files != null && files.length > 0) {
                    html.append("<ul>");
                    for (File f : files) {
                        if (f.isFile()) {
                            html.append("<li><a href='/download?file=").append(f.getName()).append("'>").append(f.getName()).append("</a></li>");
                        }
                    }
                    html.append("</ul>");
                } else {
                    html.append("<p>当前没有任何已解密导出的文件。</p>");
                }
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
