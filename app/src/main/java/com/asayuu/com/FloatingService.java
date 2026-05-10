package com.asayuu.com;

import android.app.ActivityManager;
import android.app.Service;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.TrafficStats;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.ArrayList;
import okhttp3.*;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View ballView, windowView, shieldView;
    private WindowManager.LayoutParams params, shieldParams;
    private int mWidth = 600, mHeight = 500;
    
    private RelativeLayout rootContainer;
    private LinearLayout llTransCard, llShieldCard, llMonitorCard, llCounterCard, llCounterGrid;
    private EditText etInput;
    private TextView tvResult, tvToggle, tvShieldToggle, tvCounterMode;
    private TextView tvCpu, tvRam, tvRom, tvBattery, tvNetRx, tvNetTx;
    private TextView tvTargetNet; 
    private ImageView ivCopy;
    private SeekBar sbAlpha, sbShieldAlpha, sbShieldWindowAlpha, sbMonitorWindowAlpha, sbCounterWindowAlpha;
    
    // --- 序列 Delta：暗视场 RGBA 物理寄存器 ---
    private int redVal = 0, greenVal = 0, blueVal = 0;
    private SeekBar sbRed, sbGreen, sbBlue;
    // ------------------------------------

    private Spinner spinSrcLang, spinTgtLang;
    private final String[] langNames = {"自动检测", "中文", "英语", "日语", "韩语", "法语", "德语", "俄语"};
    private final String[] langCodes = {"auto", "ZH", "EN", "JA", "KO", "FR", "DE", "RU"};

    private boolean isPassiveMode = true;
    private boolean isShieldOn = false;
    private boolean isMonitorActive = false;
    private boolean isCounterMinusMode = true;
    private int shieldAlpha = 200;
    private String lastClip = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private DBHelper db;

    private long lastRx = 0, lastTx = 0;
    private long lastCpuTotal = 0, lastCpuIdle = 0;
    private int batteryLevel = -1;
    private float batteryTemp = -1;
    
    private int targetUid = -1;
    private String targetAppName = "全局";

    private boolean isTempWarned = false;
    private boolean isLowBatteryWarned = false;
    private boolean isFullBatteryWarned = false;

    private final String[] CARD_NAMES = {"大王", "小王", "2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};
    private final int[] MAX_COUNTS = {1, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    private int[] currentCounts = new int[15];
    private TextView[] cardViews = new TextView[15];

    private OkHttpClient client;
    private volatile boolean hasResult = false;
    private volatile int failedCount = 0;
    private final List<Call> activeCalls = new ArrayList<Call>();

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (level != -1 && scale != -1) {
                batteryLevel = (int) ((level / (float) scale) * 100);
            }
            if (temp != -1) {
                batteryTemp = temp / 10.0f;
            }

            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                
                if (batteryTemp >= 42.0f && !isTempWarned) {
                    isTempWarned = true;
                    if (vibrator != null) vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
                    Toast.makeText(context, "⚠️ 警告：设备温度过高 (" + batteryTemp + "°C)", Toast.LENGTH_LONG).show();
                } else if (batteryTemp < 40.0f) {
                    isTempWarned = false;
                }

                if (batteryLevel <= 15 && !isLowBatteryWarned) {
                    isLowBatteryWarned = true;
                    if (vibrator != null) vibrator.vibrate(new long[]{0, 300, 100, 300}, -1);
                    Toast.makeText(context, "⚠️ 警告：电量极低 (" + batteryLevel + "%)", Toast.LENGTH_LONG).show();
                } else if (batteryLevel > 20) {
                    isLowBatteryWarned = false; 
                }

                if (batteryLevel == 100 && !isFullBatteryWarned) {
                    isFullBatteryWarned = true;
                    if (vibrator != null) vibrator.vibrate(500);
                    Toast.makeText(context, "✅ 提示：电池已充满，请及时拔下电源", Toast.LENGTH_SHORT).show();
                } else if (batteryLevel < 95) {
                    isFullBatteryWarned = false;
                }
            } catch (Exception e) {}
        }
    };

    private Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMonitorActive) {
                updateMonitorStats();
                mainHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        db = new DBHelper(this);
        initOkHttp();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        initParams();
        initShield();
        initBall();
        initWindow();
        windowManager.addView(ballView, params);
        initClipboardListener();
    }

    private void initOkHttp() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
            }};
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            client = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0])
                .hostnameVerifier(new HostnameVerifier() {
                    @Override public boolean verify(String hostname, SSLSession session) { return true; }
                })
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        } catch (Exception e) {
            client = new OkHttpClient();
        }
    }

    private void initParams() {
        int type = (Build.VERSION.SDK_INT >= 26) ? 2038 : 2002;
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200; params.y = 200;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
    }

    // --- 序列 Delta：物理防砖头渲染器流合并 ---
    private void applyShieldColor() {
        if (shieldView != null) {
            // 防砖头锁死：最高透明度遮蔽率限制在 230（约90%），永远保留10%可见度防止手机变砖
            int finalAlpha = Math.min(shieldAlpha, 230);
            // 将 RGBA 色彩物理降维并强行覆写入渲染层
            int color = (finalAlpha << 24) | (redVal << 16) | (greenVal << 8) | blueVal;
            shieldView.setBackgroundColor(color);
        }
    }

    private void initShield() {
        shieldView = new FrameLayout(this);
        applyShieldColor(); // 接入物理渲染引擎

        int type = (Build.VERSION.SDK_INT >= 26) ? 2038 : 2002;
        shieldParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
    }

    private void initBall() {
        ballView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        ballView.setOnTouchListener(new TouchListener(true));
    }

    private void initWindow() {
        windowView = LayoutInflater.from(this).inflate(R.layout.view_floating_window, null);
        rootContainer = (RelativeLayout) windowView.findViewById(R.id.root_container);
        llTransCard = (LinearLayout) windowView.findViewById(R.id.ll_trans_card);
        llShieldCard = (LinearLayout) windowView.findViewById(R.id.ll_shield_card);
        llMonitorCard = (LinearLayout) windowView.findViewById(R.id.ll_monitor_card);
        llCounterCard = (LinearLayout) windowView.findViewById(R.id.ll_counter_card);
        
        // --- 序列 Delta：无损动态注入暗视场面板 ---
        if (llShieldCard != null) {
            injectRgbControls();
        }
        
        spinSrcLang = (Spinner) windowView.findViewById(R.id.spin_src_lang);
        spinTgtLang = (Spinner) windowView.findViewById(R.id.spin_tgt_lang);
        
        ArrayAdapter<String> langAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, langNames);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinSrcLang != null && spinTgtLang != null) {
            spinSrcLang.setAdapter(langAdapter);
            spinTgtLang.setAdapter(langAdapter);
            spinSrcLang.setSelection(0); 
            spinTgtLang.setSelection(1); 
        }

        etInput = (EditText) windowView.findViewById(R.id.et_trans_input);
        tvResult = (TextView) windowView.findViewById(R.id.tv_trans_result);
        tvToggle = (TextView) windowView.findViewById(R.id.tv_mode_toggle);
        tvShieldToggle = (TextView) windowView.findViewById(R.id.tv_shield_toggle);
        
        tvCpu = (TextView) windowView.findViewById(R.id.tv_monitor_cpu);
        tvRam = (TextView) windowView.findViewById(R.id.tv_monitor_ram);
        tvRom = (TextView) windowView.findViewById(R.id.tv_monitor_rom);
        tvBattery = (TextView) windowView.findViewById(R.id.tv_monitor_battery);
        tvNetRx = (TextView) windowView.findViewById(R.id.tv_monitor_net_rx);
        tvNetTx = (TextView) windowView.findViewById(R.id.tv_monitor_net_tx);

        ivCopy = (ImageView) windowView.findViewById(R.id.iv_copy_trans);
        sbAlpha = (SeekBar) windowView.findViewById(R.id.sb_trans_alpha);
        sbShieldAlpha = (SeekBar) windowView.findViewById(R.id.sb_shield_alpha);
        sbShieldWindowAlpha = (SeekBar) windowView.findViewById(R.id.sb_shield_window_alpha);
        sbMonitorWindowAlpha = (SeekBar) windowView.findViewById(R.id.sb_monitor_window_alpha);
        sbCounterWindowAlpha = (SeekBar) windowView.findViewById(R.id.sb_counter_window_alpha);

        try {
            tvTargetNet = new TextView(this);
            tvTargetNet.setText("🎯 测速目标: 全局 (点击锁定当前前台App)");
            tvTargetNet.setTextColor(0xFF4A90E2);
            tvTargetNet.setTextSize(12f);
            tvTargetNet.setPadding(0, 15, 0, 0);
            tvTargetNet.setGravity(Gravity.CENTER);
            llMonitorCard.addView(tvTargetNet);

            tvTargetNet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (targetUid != -1) {
                        targetUid = -1;
                        targetAppName = "全局";
                        tvTargetNet.setText("🎯 测速目标: 全局 (点击锁定当前前台App)");
                        tvTargetNet.setTextColor(0xFF4A90E2);
                        lastRx = TrafficStats.getTotalRxBytes();
                        lastTx = TrafficStats.getTotalTxBytes();
                    } else {
                        lockForegroundAppNet();
                    }
                }
            });
        } catch (Exception e) {}

        initCounterMatrix();

        tvToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isPassiveMode = !isPassiveMode;
                tvToggle.setText("被动模式: " + (isPassiveMode ? "ON" : "OFF"));
            }
        });

        tvShieldToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isShieldOn = !isShieldOn;
                tvShieldToggle.setText("启动装甲: " + (isShieldOn ? "ON" : "OFF"));
                if (isShieldOn) {
                    try { windowManager.addView(shieldView, shieldParams); } catch (Exception e) {}
                } else {
                    try { windowManager.removeView(shieldView); } catch (Exception e) {}
                }
            }
        });

        // 绑定物理渲染通道
        sbShieldAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                shieldAlpha = p;
                applyShieldColor();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        SeekBar.OnSeekBarChangeListener windowAlphaListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                updateWindowAlpha(p);
                if (b) { 
                    if (s.getId() != R.id.sb_trans_alpha) sbAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_shield_window_alpha && sbShieldWindowAlpha != null) sbShieldWindowAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_monitor_window_alpha && sbMonitorWindowAlpha != null) sbMonitorWindowAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_counter_window_alpha && sbCounterWindowAlpha != null) sbCounterWindowAlpha.setProgress(p);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        
        sbAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        if (sbShieldWindowAlpha != null) sbShieldWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        if (sbMonitorWindowAlpha != null) sbMonitorWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        if (sbCounterWindowAlpha != null) sbCounterWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);

        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || 
                   (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String text = etInput.getText().toString().trim();
                    if (!text.isEmpty()) performTranslate(text);
                    return true;
                }
                return false;
            }
        });

        ivCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("NeoTrans", tvResult.getText()));
                Toast.makeText(FloatingService.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        
        View.OnTouchListener focusTouchListener = new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    setWindowFocusable(true);
                }
                return false;
            }
        };

        etInput.setOnTouchListener(focusTouchListener);

        View.OnKeyListener backKeyListener = new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    setWindowFocusable(false);
                    return true;
                }
                return false;
            }
        };
        etInput.setOnKeyListener(backKeyListener);

        windowView.findViewById(R.id.menu_translate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(true, false, false, false); }
        });
        windowView.findViewById(R.id.menu_shield).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(false, true, false, false); }
        });
        windowView.findViewById(R.id.menu_monitor).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(false, false, true, false); }
        });
        windowView.findViewById(R.id.menu_counter).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(false, false, false, true); }
        });

        windowView.findViewById(R.id.btn_resize).setOnTouchListener(new View.OnTouchListener() {
            private int sW, sH; private float sX, sY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) { 
                    setWindowFocusable(false); 
                    sW=params.width; sH=params.height; sX=e.getRawX(); sY=e.getRawY(); return true; 
                }
                if (e.getAction()==MotionEvent.ACTION_MOVE) {
                    params.width = Math.max(400, sW+(int)(e.getRawX()-sX));
                    params.height = Math.max(300, sH+(int)(e.getRawY()-sY));
                    mWidth=params.width; mHeight=params.height;
                    windowManager.updateViewLayout(windowView, params);
                }
                return true;
            }
        });

        windowView.findViewById(R.id.btn_minimize).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setWindowFocusable(false);
                isMonitorActive = false;
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.removeView(windowView);
                params.width=WindowManager.LayoutParams.WRAP_CONTENT; 
                params.height=WindowManager.LayoutParams.WRAP_CONTENT;
                windowManager.addView(ballView, params);
            }
        });

        windowView.findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSelf(); }
        });
        
        windowView.findViewById(R.id.btn_drag).setOnTouchListener(new TouchListener(false));
    }

    // --- 序列 Delta：动态注入物理 RGB 控制器与状态机 ---
    private void injectRgbControls() {
        try {
            TextView tvRed = new TextView(this); tvRed.setText("🔴 红色增益 (过滤蓝绿光)"); tvRed.setTextColor(0xFFE74C3C); tvRed.setTextSize(11f);
            sbRed = new SeekBar(this); sbRed.setMax(255);
            llShieldCard.addView(tvRed); llShieldCard.addView(sbRed);
            
            TextView tvGreen = new TextView(this); tvGreen.setText("🟢 绿色增益"); tvGreen.setTextColor(0xFF2ECC71); tvGreen.setTextSize(11f);
            sbGreen = new SeekBar(this); sbGreen.setMax(255);
            llShieldCard.addView(tvGreen); llShieldCard.addView(sbGreen);
            
            TextView tvBlue = new TextView(this); tvBlue.setText("🔵 蓝色增益"); tvBlue.setTextColor(0xFF3498DB); tvBlue.setTextSize(11f);
            sbBlue = new SeekBar(this); sbBlue.setMax(255);
            llShieldCard.addView(tvBlue); llShieldCard.addView(sbBlue);
            
            SeekBar.OnSeekBarChangeListener rgbListener = new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                    if (s == sbRed) redVal = p;
                    else if (s == sbGreen) greenVal = p;
                    else if (s == sbBlue) blueVal = p;
                    applyShieldColor();
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            };
            sbRed.setOnSeekBarChangeListener(rgbListener);
            sbGreen.setOnSeekBarChangeListener(rgbListener);
            sbBlue.setOnSeekBarChangeListener(rgbListener);
            
            LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.addView(createPresetBtn("🔴 战术红场", 180, 255, 0, 0, 0xFFE74C3C));
            row1.addView(createPresetBtn("🌑 绝对深渊", 210, 0, 0, 0, 0xFF95A5A6));
            
            LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.addView(createPresetBtn("🟡 护眼暖场", 100, 255, 180, 0, 0xFFF1C40F));
            row2.addView(createPresetBtn("❌ 撤除色彩", 0, 0, 0, 0, 0xFFE74C3C));
            
            llShieldCard.addView(row1);
            llShieldCard.addView(row2);
        } catch (Exception e) {}
    }

    private Button createPresetBtn(String txt, final int a, final int r, final int g, final int b, int col) {
        Button btn = new Button(this);
        btn.setText(txt); btn.setTextColor(col); btn.setTextSize(10f);
        btn.setBackgroundColor(0x00000000); // 透明底
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (sbShieldAlpha != null) sbShieldAlpha.setProgress(a);
                if (sbRed != null) sbRed.setProgress(r);
                if (sbGreen != null) sbGreen.setProgress(g);
                if (sbBlue != null) sbBlue.setProgress(b);
            }
        });
        return btn;
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        }
        return true;
    }

    private void lockForegroundAppNet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!hasUsageStatsPermission()) {
                Toast.makeText(this, "需授权[使用情况访问]以突破系统致盲", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

            try {
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                long time = System.currentTimeMillis();
                
                android.app.usage.UsageEvents events = usm.queryEvents(time - 1000 * 60, time);
                android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
                String currentApp = null;
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        currentApp = event.getPackageName();
                    }
                }
                
                if (currentApp != null) {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo ai = pm.getApplicationInfo(currentApp, 0);
                    targetUid = ai.uid;
                    targetAppName = pm.getApplicationLabel(ai).toString();
                    
                    tvTargetNet.setText("🎯 锁定: " + targetAppName + " [解除]");
                    tvTargetNet.setTextColor(0xFFE67E22);
                    lastRx = TrafficStats.getUidRxBytes(targetUid);
                    lastTx = TrafficStats.getUidTxBytes(targetUid);
                    return;
                }
            } catch (Exception e) {}
        } else {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    String pkg = tasks.get(0).topActivity.getPackageName();
                    PackageManager pm = getPackageManager();
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    targetUid = ai.uid;
                    targetAppName = pm.getApplicationLabel(ai).toString();
                    
                    tvTargetNet.setText("🎯 锁定: " + targetAppName + " [解除]");
                    tvTargetNet.setTextColor(0xFFE67E22);
                    lastRx = TrafficStats.getUidRxBytes(targetUid);
                    lastTx = TrafficStats.getUidTxBytes(targetUid);
                    return;
                }
            } catch (Exception e) {}
        }
        
        Toast.makeText(this, "物理探针抓取失败", Toast.LENGTH_SHORT).show();
    }

    private void setWindowFocusable(boolean focusable) {
        if (windowView == null || windowView.getParent() == null) return;
        boolean isCurrentlyFocusable = (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
        if (focusable && !isCurrentlyFocusable) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(windowView, params);
        } else if (!focusable && isCurrentlyFocusable) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(windowView, params);
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (windowView != null) {
            imm.hideSoftInputFromWindow(windowView.getWindowToken(), 0);
        }
    }

    private void initCounterMatrix() {
        llCounterGrid = (LinearLayout) windowView.findViewById(R.id.ll_counter_grid);
        tvCounterMode = (TextView) windowView.findViewById(R.id.btn_counter_mode);

        tvCounterMode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isCounterMinusMode = !isCounterMinusMode;
                tvCounterMode.setText(isCounterMinusMode ? "[ 模式: 扣除 - ]" : "[ 模式: 恢复 + ]");
                tvCounterMode.setTextColor(isCounterMinusMode ? 0xFF4E5D6A : 0xFF27AE60);
            }
        });

        windowView.findViewById(R.id.btn_counter_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetCounter(); }
        });

        LinearLayout currentRow = null;
        for (int i = 0; i < 15; i++) {
            if (i % 4 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = 15;
                llCounterGrid.addView(currentRow, rowParams);
            }
            
            final int index = i;
            final TextView tvCard = new TextView(this);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, 100, 1.0f);
            tvParams.setMargins(8, 0, 8, 0);
            tvCard.setLayoutParams(tvParams);
            tvCard.setGravity(Gravity.CENTER);
            tvCard.setTextSize(13f);
            
            tvCard.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (isCounterMinusMode) {
                        if (currentCounts[index] > 0) {
                            currentCounts[index]--;
                            updateCardUI(index);
                        }
                    } else {
                        if (currentCounts[index] < MAX_COUNTS[index]) {
                            currentCounts[index]++;
                            updateCardUI(index);
                        }
                    }
                }
            });

            tvCard.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (isCounterMinusMode) {
                        currentCounts[index] = 0;
                    } else {
                        currentCounts[index] = MAX_COUNTS[index];
                    }
                    updateCardUI(index);
                    return true;
                }
            });
            
            cardViews[i] = tvCard;
            currentRow.addView(tvCard);
        }
        resetCounter();
    }
    
    private void updateCardUI(int i) {
        cardViews[i].setText(CARD_NAMES[i] + "\n[" + currentCounts[i] + "]");
        if (currentCounts[i] == 0) {
            cardViews[i].setTextColor(0xFF999999); 
            cardViews[i].setBackgroundResource(R.drawable.nm_card_inset); 
        } else {
            cardViews[i].setTextColor(0xFFE74C3C); 
            cardViews[i].setBackgroundResource(R.drawable.nm_button_raised);
        }
    }

    private void resetCounter() {
        System.arraycopy(MAX_COUNTS, 0, currentCounts, 0, 15);
        for (int i = 0; i < 15; i++) {
            updateCardUI(i);
        }
        isCounterMinusMode = true;
        tvCounterMode.setText("[ 模式: 扣除 - ]");
        tvCounterMode.setTextColor(0xFF4E5D6A);
    }

    private void switchTab(boolean showTrans, boolean showShield, boolean showMonitor, boolean showCounter) {
        setWindowFocusable(false); 
        llTransCard.setVisibility(showTrans ? View.VISIBLE : View.GONE);
        llShieldCard.setVisibility(showShield ? View.VISIBLE : View.GONE);
        llMonitorCard.setVisibility(showMonitor ? View.VISIBLE : View.GONE);
        llCounterCard.setVisibility(showCounter ? View.VISIBLE : View.GONE);
        
        isMonitorActive = showMonitor;
        if (showMonitor) {
            mainHandler.removeCallbacks(monitorRunnable);
            mainHandler.post(monitorRunnable);
        }
    }

    private void updateMonitorStats() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")), 1000);
            String load = reader.readLine();
            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long total = 0;
            for (int i = 1; i < toks.length; i++) {
                total += Long.parseLong(toks[i]);
            }
            if (lastCpuTotal != 0) {
                long diffTotal = total - lastCpuTotal;
                long diffIdle = idle - lastCpuIdle;
                if (diffTotal > 0) {
                    int cpuUsage = (int) ((diffTotal - diffIdle) * 100 / diffTotal);
                    tvCpu.setText("CPU 占用率: " + Math.min(100, Math.max(0, cpuUsage)) + "%");
                }
            }
            lastCpuTotal = total;
            lastCpuIdle = idle;
        } catch (Exception e) {
            tvCpu.setText("CPU 占用率: 系统已限制读取");
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ex) {}
            }
        }

        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long totalMegs = mi.totalMem / 1048576L;
            long availMegs = mi.availMem / 1048576L;
            long usedMegs = totalMegs - availMegs;
            tvRam.setText("RAM 占用率: " + usedMegs + " MB / " + totalMegs + " MB");
        } catch (Exception e) {}

        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize, totalBlocks, availableBlocks;
            if (Build.VERSION.SDK_INT >= 18) {
                blockSize = stat.getBlockSizeLong();
                totalBlocks = stat.getBlockCountLong();
                availableBlocks = stat.getAvailableBlocksLong();
            } else {
                blockSize = (long) stat.getBlockSize();
                totalBlocks = (long) stat.getBlockCount();
                availableBlocks = (long) stat.getAvailableBlocks();
            }
            long totalRom = (totalBlocks * blockSize) / 1048576L;
            long availRom = (availableBlocks * blockSize) / 1048576L;
            long usedRom = totalRom - availRom;
            tvRom.setText("ROM 占用率: " + usedRom + " MB / " + totalRom + " MB");
        } catch (Exception e) {}

        if (batteryLevel != -1) {
            tvBattery.setText("电池状态: " + batteryLevel + "% | 温度: " + batteryTemp + "°C");
            if (batteryTemp >= 42.0f || batteryLevel <= 15) {
                tvBattery.setTextColor(0xFFE74C3C); 
            } else {
                tvBattery.setTextColor(0xFF4E5D6A); 
            }
        }

        try {
            long rx = targetUid == -1 ? TrafficStats.getTotalRxBytes() : TrafficStats.getUidRxBytes(targetUid);
            long tx = targetUid == -1 ? TrafficStats.getTotalTxBytes() : TrafficStats.getUidTxBytes(targetUid);
            
            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
                tvNetRx.setText("下行速率: 接口受限");
                tvNetTx.setText("上行速率: 接口受限");
                tvNetRx.setTextColor(0xFF4E5D6A);
                tvNetTx.setTextColor(0xFF4E5D6A);
            } else if (targetUid != -1 && rx == 0 && tx == 0) {
                tvNetRx.setText("下行速率: [受 eBPF 隔离致盲]");
                tvNetTx.setText("上行速率: [受 eBPF 隔离致盲]");
                tvNetRx.setTextColor(0xFFE74C3C);
                tvNetTx.setTextColor(0xFFE74C3C);
            } else {
                if (lastRx != 0 || lastTx != 0) {
                    long rxSpeed = (rx - lastRx) / 1024;
                    long txSpeed = (tx - lastTx) / 1024;
                    tvNetRx.setText("下行速率: " + Math.max(0, rxSpeed) + " KB/s");
                    tvNetTx.setText("上行速率: " + Math.max(0, txSpeed) + " KB/s");
                } else {
                    tvNetRx.setText("下行速率: 0 KB/s");
                    tvNetTx.setText("上行速率: 0 KB/s");
                }
                tvNetRx.setTextColor(0xFF4E5D6A);
                tvNetTx.setTextColor(0xFF4E5D6A);
                lastRx = rx;
                lastTx = tx;
            }
        } catch (Exception e) {}
    }

    private void updateWindowAlpha(int p) {
        if (rootContainer != null && rootContainer.getBackground()!=null) rootContainer.getBackground().mutate().setAlpha(p);
        if (llTransCard != null && llTransCard.getBackground()!=null) llTransCard.getBackground().mutate().setAlpha(p);
        if (llShieldCard != null && llShieldCard.getBackground()!=null) llShieldCard.getBackground().mutate().setAlpha(p);
        if (llMonitorCard != null && llMonitorCard.getBackground()!=null) llMonitorCard.getBackground().mutate().setAlpha(p);
        if (llCounterCard != null && llCounterCard.getBackground()!=null) llCounterCard.getBackground().mutate().setAlpha(p);
        if (etInput != null && etInput.getBackground()!=null) etInput.getBackground().mutate().setAlpha(p);
        if (tvResult != null && tvResult.getBackground()!=null) tvResult.getBackground().mutate().setAlpha(p);
    }

    private class TouchListener implements View.OnTouchListener {
        private boolean isB; private int iX, iY; private float itX, itY;
        public TouchListener(boolean b) { isB=b; }
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_DOWN) { 
                if (!isB) setWindowFocusable(false);
                iX=params.x; iY=params.y; itX=e.getRawX(); itY=e.getRawY(); return true; 
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                params.x=iX+(int)(e.getRawX()-itX); params.y=iY+(int)(e.getRawY()-itY);
                windowManager.updateViewLayout(isB?ballView:windowView, params);
                return true;
            }
            if (isB && e.getAction()==MotionEvent.ACTION_UP && Math.abs(e.getRawX()-itX)<10) {
                windowManager.removeView(ballView);
                params.width=mWidth; params.height=mHeight; 
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.addView(windowView, params);
            }
            return true;
        }
    }

    private void initClipboardListener() {
        final ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.addPrimaryClipChangedListener(new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override public void onPrimaryClipChanged() {
                ClipData d = cb.getPrimaryClip();
                if (d!=null && d.getItemCount()>0) {
                    CharSequence t = d.getItemAt(0).getText();
                    if (t!=null && !t.toString().equals(lastClip)) {
                        lastClip = t.toString();
                        db.addClip(lastClip);
                        if (isPassiveMode) performTranslate(lastClip);
                    }
                }
            }
        });
    }

    private synchronized void checkAllFailed() {
        if (hasResult) return;
        failedCount++;
        if (failedCount >= 2) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (!hasResult) tvResult.setText("全节点竞速失败或网络超时");
                }
            });
        }
    }

    private void performTranslate(final String text) {
        tvResult.setText("引擎竞速中...");
        hasResult = false;
        failedCount = 0;
        
        synchronized(activeCalls) {
            for (Call call : activeCalls) {
                if (call != null && !call.isCanceled()) {
                    call.cancel();
                }
            }
            activeCalls.clear();
        }

        int sIdx = spinSrcLang != null ? spinSrcLang.getSelectedItemPosition() : 0;
        int tIdx = spinTgtLang != null ? spinTgtLang.getSelectedItemPosition() : 1;
        final String sLang = langCodes[sIdx];
        final String tLang = langCodes[tIdx];

        final String[] nodes = {
            "https://deeplx.jayogo.com/translate/sk-D0TB8dagu1yxZoLrmOdenfcugyf82D14zTZoUUTRLoQFx9OJ",
            "https://deeplx.mingming.dev/translate"
        };

        for (final String url : nodes) {
            try {
                JSONObject j = new JSONObject(); 
                j.put("text", text); 
                j.put("source_lang", sLang); 
                j.put("target_lang", tLang);
                
                RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), j.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                        .build();

                Call call = client.newCall(request);
                synchronized(activeCalls) {
                    activeCalls.add(call);
                }

                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        checkAllFailed();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                String resBody = response.body().string();
                                JSONObject resObj = new JSONObject(resBody);
                                final String data = resObj.optString("data");

                                if (data != null && !data.isEmpty() && !hasResult) {
                                    if (data.contains("linux.do") || (data.startsWith("http") && !text.startsWith("http"))) {
                                        checkAllFailed();
                                        return;
                                    }
                                    
                                    hasResult = true;
                                    synchronized(activeCalls) {
                                        for (Call c : activeCalls) {
                                            if (!c.isCanceled()) c.cancel();
                                        }
                                    }
                                    mainHandler.post(new Runnable() {
                                        @Override public void run() {
                                            tvResult.setText(data);
                                            if (ivCopy != null) ivCopy.setVisibility(View.VISIBLE);
                                        }
                                    });
                                } else {
                                    checkAllFailed();
                                }
                            } else {
                                checkAllFailed();
                            }
                        } catch (Exception e) {
                            checkAllFailed();
                        } finally {
                            if (response != null) {
                                response.close();
                            }
                        }
                    }
                });
            } catch (Exception e) {
                checkAllFailed();
            }
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        try { unregisterReceiver(batteryReceiver); } catch (Exception e) {}
        isMonitorActive = false;
        mainHandler.removeCallbacks(monitorRunnable);
        
        synchronized(activeCalls) {
            for (Call call : activeCalls) {
                if (call != null) call.cancel();
            }
            activeCalls.clear();
        }

        if (isShieldOn && shieldView != null && shieldView.getParent() != null) {
            try { windowManager.removeView(shieldView); } catch (Exception e) {}
        }
        if (ballView != null && ballView.getParent()!=null) windowManager.removeView(ballView);
        if (windowView != null && windowView.getParent()!=null) windowManager.removeView(windowView);
        super.onDestroy();
    }
}
