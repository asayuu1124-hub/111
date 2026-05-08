package com.asayuu.com;

import android.app.ActivityManager;
import android.app.Service;
import android.content.*;
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
    private ImageView ivCopy;
    private SeekBar sbAlpha, sbShieldAlpha, sbShieldWindowAlpha, sbMonitorWindowAlpha, sbCounterWindowAlpha;

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

    // --- 主动告警状态机 ---
    private boolean isTempWarned = false;
    private boolean isLowBatteryWarned = false;
    private boolean isFullBatteryWarned = false;

    // --- Counter Matrix Data ---
    private final String[] CARD_NAMES = {"大王", "小王", "2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};
    private final int[] MAX_COUNTS = {1, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    private int[] currentCounts = new int[15];
    private TextView[] cardViews = new TextView[15];

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

            // 物理探针主动告警系统
            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                
                // 温控警告 (≥ 42°C)
                if (batteryTemp >= 42.0f && !isTempWarned) {
                    isTempWarned = true;
                    if (vibrator != null) vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
                    Toast.makeText(context, "⚠️ 警告：设备温度过高 (" + batteryTemp + "°C)", Toast.LENGTH_LONG).show();
                } else if (batteryTemp < 40.0f) {
                    isTempWarned = false; // 降温后重置
                }

                // 低电量警告 (≤ 15%)
                if (batteryLevel <= 15 && !isLowBatteryWarned) {
                    isLowBatteryWarned = true;
                    if (vibrator != null) vibrator.vibrate(new long[]{0, 300, 100, 300}, -1);
                    Toast.makeText(context, "⚠️ 警告：电量极低 (" + batteryLevel + "%)", Toast.LENGTH_LONG).show();
                } else if (batteryLevel > 20) {
                    isLowBatteryWarned = false; // 充电后重置
                }

                // 满电提醒
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
        trustAllSSL();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        initParams();
        initShield();
        initBall();
        initWindow();
        windowManager.addView(ballView, params);
        initClipboardListener();
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

    private void initParams() {
        int type = (Build.VERSION.SDK_INT >= 26) ? 2038 : 2002;
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200; params.y = 200;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
    }

    private void initShield() {
        shieldView = new FrameLayout(this);
        shieldView.setBackgroundColor(0xFF000000); 
        shieldView.getBackground().mutate().setAlpha(shieldAlpha);

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

        sbShieldAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                shieldAlpha = p;
                if (shieldView != null && shieldView.getBackground() != null) {
                    shieldView.getBackground().mutate().setAlpha(shieldAlpha);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        SeekBar.OnSeekBarChangeListener windowAlphaListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                updateWindowAlpha(p);
                if (b) { 
                    if (s.getId() != R.id.sb_trans_alpha) sbAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_shield_window_alpha) sbShieldWindowAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_monitor_window_alpha) sbMonitorWindowAlpha.setProgress(p);
                    if (s.getId() != R.id.sb_counter_window_alpha) sbCounterWindowAlpha.setProgress(p);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        
        sbAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        sbShieldWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        sbMonitorWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);
        sbCounterWindowAlpha.setOnSeekBarChangeListener(windowAlphaListener);

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
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")), 1000);
            String load = reader.readLine();
            reader.close();
            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long total = 0;
            for (int i = 1; i < toks.length; i++) {
                total += Long.parseLong(toks[i]);
            }
            if (lastCpuTotal != 0) {
                long diffTotal = total - lastCpuTotal;
                long diffIdle = idle - lastCpuIdle;
                int cpuUsage = (int) ((diffTotal - diffIdle) * 100 / diffTotal);
                tvCpu.setText("CPU 占用率: " + cpuUsage + "%");
            }
            lastCpuTotal = total;
            lastCpuIdle = idle;
        } catch (Exception e) {
            tvCpu.setText("CPU 占用率: 系统已限制读取");
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
            // 动态告警 UI 变色
            if (batteryTemp >= 42.0f || batteryLevel <= 15) {
                tvBattery.setTextColor(0xFFE74C3C); // 警示红
            } else {
                tvBattery.setTextColor(0xFF4E5D6A); // 原色
            }
        }

        try {
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            if (lastRx != 0 || lastTx != 0) {
                long rxSpeed = (rx - lastRx) / 1024;
                long txSpeed = (tx - lastTx) / 1024;
                tvNetRx.setText("下行速率: " + rxSpeed + " KB/s");
                tvNetTx.setText("上行速率: " + txSpeed + " KB/s");
            }
            lastRx = rx;
            lastTx = tx;
        } catch (Exception e) {}
    }

    private void updateWindowAlpha(int p) {
        if (rootContainer.getBackground()!=null) rootContainer.getBackground().mutate().setAlpha(p);
        if (llTransCard.getBackground()!=null) llTransCard.getBackground().mutate().setAlpha(p);
        if (llShieldCard.getBackground()!=null) llShieldCard.getBackground().mutate().setAlpha(p);
        if (llMonitorCard.getBackground()!=null) llMonitorCard.getBackground().mutate().setAlpha(p);
        if (llCounterCard.getBackground()!=null) llCounterCard.getBackground().mutate().setAlpha(p);
        if (etInput.getBackground()!=null) etInput.getBackground().mutate().setAlpha(p);
        if (tvResult.getBackground()!=null) tvResult.getBackground().mutate().setAlpha(p);
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

    private void performTranslate(final String text) {
        tvResult.setText("正在翻译...");
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = doTranslate(text);
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (result != null) {
                            tvResult.setText(result);
                            ivCopy.setVisibility(View.VISIBLE);
                        } else {
                            tvResult.setText("翻译失败或接口超时");
                        }
                    }
                });
            }
        }).start();
    }

    private String doTranslate(String text) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://deeplx.jayogo.com/translate/sk-D0TB8dagu1yxZoLrmOdenfcugyf82D14zTZoUUTRLoQFx9OJ").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");
            JSONObject j = new JSONObject(); j.put("text", text); j.put("source_lang", "auto"); j.put("target_lang", "ZH");
            c.getOutputStream().write(j.toString().getBytes("UTF-8"));
            if (c.getResponseCode()==200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder s = new StringBuilder(); String l;
                while((l=r.readLine())!=null) s.append(l);
                return new JSONObject(s.toString()).optString("data");
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        unregisterReceiver(batteryReceiver);
        isMonitorActive = false;
        mainHandler.removeCallbacks(monitorRunnable);
        if (isShieldOn && shieldView != null && shieldView.getParent() != null) {
            try { windowManager.removeView(shieldView); } catch (Exception e) {}
        }
        if (ballView != null && ballView.getParent()!=null) windowManager.removeView(ballView);
        if (windowView != null && windowView.getParent()!=null) windowManager.removeView(windowView);
        super.onDestroy();
    }
}
