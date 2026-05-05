package com.asayuu.com;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private TextView tvClock, tvBattery, tvClipboard, tvNotice, tvNetSpeed;
    private ProgressBar pbCpu, pbRam, pbRom;
    private Button btnStartFloat, btnToWatermark, btnBackLogin, btnExit, btnToVideoSite, btnToNovelSite, btnToAppManager, btnToSafeBox;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timer;
    private BroadcastReceiver batteryReceiver;
    private SharedPreferences sp;
    private UserTask userTask;
    private DBHelper db;

    private long lastTotalCpu = 0;
    private long lastIdleCpu = 0;
    private long lastRxBytes = 0;
    private long lastTxBytes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("asayuu_config", MODE_PRIVATE);
        db = new DBHelper(this);
        
        if (!sp.getBoolean("isLoggedIn", false) && !sp.getBoolean("auto_login", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvBattery = (TextView) findViewById(R.id.tv_battery);
        tvClipboard = (TextView) findViewById(R.id.tv_clipboard_preview);
        tvNotice = (TextView) findViewById(R.id.tv_notice_content);
        tvNetSpeed = (TextView) findViewById(R.id.tv_net_speed);
        
        pbCpu = (ProgressBar) findViewById(R.id.pb_cpu);
        pbRam = (ProgressBar) findViewById(R.id.pb_ram);
        pbRom = (ProgressBar) findViewById(R.id.pb_rom);

        btnStartFloat = (Button) findViewById(R.id.btn_start_float);
        btnToWatermark = (Button) findViewById(R.id.btn_to_watermark);
        btnToVideoSite = (Button) findViewById(R.id.btn_to_video_site);
        btnToNovelSite = (Button) findViewById(R.id.btn_to_novel_site);
        btnToAppManager = (Button) findViewById(R.id.btn_to_app_manager);
        btnToSafeBox = (Button) findViewById(R.id.btn_to_safebox);
        btnBackLogin = (Button) findViewById(R.id.btn_back_login);
        btnExit = (Button) findViewById(R.id.btn_exit);

        initClickListeners();
        fetchCloudData(); // 核心：恢復公告與更新檢查
        
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        startDashboard();
    }

    private void initClickListeners() {
        btnStartFloat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(MainActivity.this)) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                } else {
                    startService(new Intent(MainActivity.this, FloatingService.class));
                }
            }
        });

        btnToAppManager.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 100);
                } else {
                    Intent it = new Intent(MainActivity.this, com.asayuu.com.AppManagerActivity.class);
                    startActivity(it);
                }
            }
        });
        
        btnToSafeBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SafeBoxActivity.class));
            }
        });

        findViewById(R.id.layout_clip_history).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showClipboardHistoryDialog(); }
        });

        btnToWatermark.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, WatermarkActivity.class)); } });
        btnToVideoSite.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showVideoChoiceDialog(); } });
        btnToNovelSite.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showNovelChoiceDialog(); } });
        
        btnBackLogin.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) { 
                sp.edit().putBoolean("isLoggedIn", false).putBoolean("auto_login", false).apply(); 
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish(); 
            } 
        });
        
        btnExit.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) { 
                sp.edit().putBoolean("isLoggedIn", false).apply();
                finishAffinity(); 
                System.exit(0); 
            } 
        });
    }

    // 🛡️ 重新恢復的雲端管控引擎
    private void fetchCloudData() {
        userTask = new UserTask();
        userTask.execute("get_notice", "", "", new UserTask.UserCallback() {
            @Override public void onResult(boolean success, String message) {
                if (!success) return;
                try {
                    JSONObject cloudJson = new JSONObject(message);
                    
                    // 1. 強制更新檢查
                    int cloudVersion = cloudJson.optInt("version_code", 0);
                    final String updateUrl = cloudJson.optString("update_url", "");
                    int localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

                    if (cloudVersion > localVersion && !updateUrl.isEmpty()) {
                        showUpdateDialog(updateUrl);
                        return; // 優先處理更新，不再彈出普通公告
                    }

                    // 2. 更新跑馬燈文字
                    tvNotice.setText(cloudJson.optString("content", "歡迎使用小欲 v2.0"));
                    
                    // 3. 公告彈窗檢查（具備重複彈出過濾邏輯）
                    String cloudPopup = cloudJson.optString("popup", "");
                    if (!cloudPopup.isEmpty()) {
                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        String lastIgnoreDate = sp.getString("last_notice_ignore_date", "");
                        String lastIgnoreContent = sp.getString("last_notice_ignore_content", "");
                        
                        // 若公告內容更新，或今天還未展示過，則彈出
                        if (!cloudPopup.equals(lastIgnoreContent) || !today.equals(lastIgnoreDate)) {
                            showNoticeDialog(cloudPopup, today);
                        }
                    }
                } catch (Exception e) {}
            }
        });
    }

    private void showNoticeDialog(final String content, final String todayDate) {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_notice, null);
        TextView tvMsg = (TextView) view.findViewById(R.id.dialog_msg);
        Button btnOk = (Button) view.findViewById(R.id.dialog_btn_ok);
        TextView btnIgnore = (TextView) view.findViewById(R.id.dialog_btn_ignore);
        
        tvMsg.setText(content);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        btnOk.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        btnIgnore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putString("last_notice_ignore_date", todayDate)
                        .putString("last_notice_ignore_content", content).apply();
                dialog.dismiss();
            }
        });
        dialog.show();
        resizeDialog(dialog);
    }

    private void showUpdateDialog(final String url) {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_notice, null);
        ((TextView) view.findViewById(R.id.dialog_title)).setText("🚀 發現新版本");
        ((TextView) view.findViewById(R.id.dialog_msg)).setText("請升級至最新版本以獲得更好的體驗。");
        Button btnUpdate = (Button) view.findViewById(R.id.dialog_btn_ok);
        btnUpdate.setText("立即下載");
        view.findViewById(R.id.dialog_btn_ignore).setVisibility(View.GONE);
        
        dialog.setCancelable(false); // 強制更新不可取消
        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        });
        dialog.show();
        resizeDialog(dialog);
    }

    private void startDashboard() {
        timer = new Runnable() {
            @Override public void run() {
                try {
                    tvClock.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                    updateHardwareStats();
                } catch (Exception e) {}
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timer);

        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                tvBattery.setText("電量监控: " + level + "%");
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void updateHardwareStats() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long total = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4]);
            if (lastTotalCpu != 0 && total > lastTotalCpu) {
                long diffTotal = total - lastTotalCpu;
                long diffIdle = idle - lastIdleCpu;
                int cpuUsage = (int) (100 * (diffTotal - diffIdle) / diffTotal);
                pbCpu.setProgress(Math.max(0, Math.min(100, cpuUsage)));
            }
            lastTotalCpu = total; lastIdleCpu = idle;
            reader.close();
        } catch (Exception e) {}

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        pbRam.setProgress((int) (100 * (mi.totalMem - mi.availMem) / mi.totalMem));

        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long blocks = stat.getBlockCount();
        if (blocks > 0) pbRom.setProgress((int) (100.0 * stat.getAvailableBlocks() / blocks));
        
        long rxBytes = TrafficStats.getTotalRxBytes();
        long txBytes = TrafficStats.getTotalTxBytes();
        if (rxBytes != TrafficStats.UNSUPPORTED && txBytes != TrafficStats.UNSUPPORTED) {
            long rxSpeed = (rxBytes - lastRxBytes) / 1024;
            long txSpeed = (txBytes - lastTxBytes) / 1024;
            tvNetSpeed.setText("网络测速: ↓ " + Math.max(0, rxSpeed) + " KB/s | ↑ " + Math.max(0, txSpeed) + " KB/s");
            lastRxBytes = rxBytes; lastTxBytes = txBytes;
        }
    }

    private void showClipboardHistoryDialog() {
        final List<String> clips = db.getClips();
        if (clips.isEmpty()) { Toast.makeText(this, "暂无剪贴板历史", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📋 剪贴板历史");
        final String[] items = clips.toArray(new String[0]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Neo", items[which]));
                Toast.makeText(MainActivity.this, "已回填", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setPositiveButton("关闭", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        resizeDialog(dialog);
    }

    private void showVideoChoiceDialog() {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_video_choice, null);
        View.OnClickListener siteListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = ""; int id = v.getId();
                if (id == R.id.btn_site_huazi) url = "https://www.huazidm.com/";
                else if (id == R.id.btn_site_skr) url = "https://www.skr2.cc/";
                else if (id == R.id.btn_site_fl) url = "https://www.aafun.cc/";
                else if (id == R.id.btn_site_fandazi) url = "https://fdzys.com/";
                else if (id == R.id.btn_site_kptv) url = "https://kptv.app/";
                if (!url.isEmpty()) { Intent intent = new Intent(MainActivity.this, VideoSiteActivity.class); intent.putExtra("target_url", url); startActivity(intent); dialog.dismiss(); }
            }
        };
        view.findViewById(R.id.btn_site_huazi).setOnClickListener(siteListener);
        view.findViewById(R.id.btn_site_skr).setOnClickListener(siteListener);
        view.findViewById(R.id.btn_site_fl).setOnClickListener(siteListener);
        view.findViewById(R.id.btn_site_fandazi).setOnClickListener(siteListener);
        view.findViewById(R.id.btn_site_kptv).setOnClickListener(siteListener);
        view.findViewById(R.id.btn_close_choice).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show(); resizeDialog(dialog);
    }

    private void showNovelChoiceDialog() {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_novel_choice, null);
        Button btnSearch = (Button) view.findViewById(R.id.btn_novel_search);
        Button btn520 = (Button) view.findViewById(R.id.btn_novel_520);
        TextView btnClose = (TextView) view.findViewById(R.id.btn_close_novel);
        View.OnClickListener novelListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = ""; int id = v.getId();
                if (id == R.id.btn_novel_search) url = "https://owlook.com.cn/";
                else if (id == R.id.btn_novel_520) url = "https://502book.com/";
                if (!url.isEmpty()) {
                    Intent intent = new Intent(MainActivity.this, NovelSiteActivity.class);
                    intent.putExtra("target_url", url);
                    startActivity(intent); dialog.dismiss();
                }
            }
        };
        btnSearch.setOnClickListener(novelListener);
        btn520.setOnClickListener(novelListener);
        btnClose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show(); resizeDialog(dialog);
    }

    private void resizeDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(lp);
        }
    }

    @Override
    protected void onDestroy() {
        if (handler != null && timer != null) handler.removeCallbacks(timer);
        if (batteryReceiver != null) { try { unregisterReceiver(batteryReceiver); } catch (Exception e) {} }
        super.onDestroy();
    }
}