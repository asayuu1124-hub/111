package com.asayuu.com;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView tvClock, tvBattery, tvClipboard, tvNotice;
    private Button btnStartFloat, btnToWatermark, btnBackLogin, btnExit, btnToVideoSite, btnToNovelSite;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timer;
    private BroadcastReceiver batteryReceiver;
    private SharedPreferences sp;
    private UserTask userTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("asayuu_config", MODE_PRIVATE);
        
        if (!sp.getBoolean("isLoggedIn", false) && !sp.getBoolean("auto_login", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        userTask = new UserTask();
        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvBattery = (TextView) findViewById(R.id.tv_battery);
        tvClipboard = (TextView) findViewById(R.id.tv_clipboard_preview);
        tvNotice = (TextView) findViewById(R.id.tv_notice_content);
        
        btnStartFloat = (Button) findViewById(R.id.btn_start_float);
        btnToWatermark = (Button) findViewById(R.id.btn_to_watermark);
        btnToVideoSite = (Button) findViewById(R.id.btn_to_video_site);
        btnToNovelSite = (Button) findViewById(R.id.btn_to_novel_site);
        btnBackLogin = (Button) findViewById(R.id.btn_back_login);
        btnExit = (Button) findViewById(R.id.btn_exit);

        initClickListeners();
        fetchCloudData();
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

        btnToWatermark.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WatermarkActivity.class));
            }
        });

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

    private void fetchCloudData() {
        userTask.execute("get_notice", "", "", new UserTask.UserCallback() {
            @Override
            public void onResult(boolean success, String message) {
                if (!success) return;
                try {
                    JSONObject cloudJson = new JSONObject(message);
                    int cloudVersion = cloudJson.optInt("version_code", 0);
                    final String updateUrl = cloudJson.optString("update_url", "");
                    int localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

                    if (cloudVersion > localVersion && !updateUrl.isEmpty()) {
                        showUpdateDialog(updateUrl);
                        return;
                    }

                    tvNotice.setText(cloudJson.optString("content", "欢迎使用小欲 v1.5"));
                    
                    String cloudPopup = cloudJson.optString("popup", "");
                    if (!cloudPopup.isEmpty()) {
                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        String lastIgnoreDate = sp.getString("last_notice_ignore_date", "");
                        String lastIgnoreContent = sp.getString("last_notice_ignore_content", "");
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
                sp.edit().putString("last_notice_ignore_date", todayDate).putString("last_notice_ignore_content", content).apply();
                dialog.dismiss();
            }
        });
        dialog.show();
        resizeDialog(dialog);
    }

    private void showUpdateDialog(final String url) {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_notice, null);
        ((TextView) view.findViewById(R.id.dialog_title)).setText("🚀 发现新版本");
        ((TextView) view.findViewById(R.id.dialog_msg)).setText("请升级至最新版本以获得更好的体验。");
        Button btnUpdate = (Button) view.findViewById(R.id.dialog_btn_ok);
        btnUpdate.setText("立即下载");
        view.findViewById(R.id.dialog_btn_ignore).setVisibility(View.GONE);
        dialog.setCancelable(false);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        });
        dialog.show();
        resizeDialog(dialog);
    }

    private void showVideoChoiceDialog() {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_video_choice, null);
        
        Button btnHuazi = (Button) view.findViewById(R.id.btn_site_huazi);
        Button btnSkr = (Button) view.findViewById(R.id.btn_site_skr);
        Button btnFl = (Button) view.findViewById(R.id.btn_site_fl);
        Button btnFandazi = (Button) view.findViewById(R.id.btn_site_fandazi);
        Button btnKptv = (Button) view.findViewById(R.id.btn_site_kptv);
        Button btnHuoche = (Button) view.findViewById(R.id.btn_site_huoche);
        TextView btnClose = (TextView) view.findViewById(R.id.btn_close_choice);

        View.OnClickListener siteListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "";
                int id = v.getId();
                if (id == R.id.btn_site_huazi) url = "https://www.huazidm.com/";
                else if (id == R.id.btn_site_skr) url = "https://www.skr2.cc/";
                else if (id == R.id.btn_site_fl) url = "https://www.aafun.cc/";
                else if (id == R.id.btn_site_fandazi) url = "https://fdzys.com/";
                else if (id == R.id.btn_site_kptv) url = "https://kptv.app/";
                else if (id == R.id.btn_site_huoche) url = "https://www.tdgo.shop/";
                if (!url.isEmpty()) {
                    Intent intent = new Intent(MainActivity.this, VideoSiteActivity.class);
                    intent.putExtra("target_url", url);
                    startActivity(intent);
                    dialog.dismiss();
                }
            }
        };

        btnHuazi.setOnClickListener(siteListener);
        btnSkr.setOnClickListener(siteListener); btnFl.setOnClickListener(siteListener);
        btnFandazi.setOnClickListener(siteListener); btnKptv.setOnClickListener(siteListener);
        btnHuoche.setOnClickListener(siteListener);
        btnClose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });

        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        resizeDialog(dialog);
    }

    private void showNovelChoiceDialog() {
        final Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_novel_choice, null);
        
        Button btnSearch = (Button) view.findViewById(R.id.btn_novel_search);
        Button btnShuqu = (Button) view.findViewById(R.id.btn_novel_shuqu);
        Button btnBqg = (Button) view.findViewById(R.id.btn_novel_bqg);
        Button btn520 = (Button) view.findViewById(R.id.btn_novel_520);
        TextView btnClose = (TextView) view.findViewById(R.id.btn_close_novel);

        View.OnClickListener novelListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "";
                int id = v.getId();
                if (id == R.id.btn_novel_search) url = "https://owlook.com.cn/";
                else if (id == R.id.btn_novel_shuqu) url = "https://m.shuquge.com/";
                else if (id == R.id.btn_novel_bqg) url = "https://m.bqg99.cc/";
                else if (id == R.id.btn_novel_520) url = "https://502book.com/";
                if (!url.isEmpty()) {
                    Intent intent = new Intent(MainActivity.this, NovelSiteActivity.class);
                    intent.putExtra("target_url", url);
                    startActivity(intent);
                    dialog.dismiss();
                }
            }
        };

        btnSearch.setOnClickListener(novelListener);
        btnShuqu.setOnClickListener(novelListener); btnBqg.setOnClickListener(novelListener);
        btn520.setOnClickListener(novelListener);
        btnClose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });

        dialog.setContentView(view);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        resizeDialog(dialog);
    }

    private void resizeDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(lp);
        }
    }

    private void startDashboard() {
        timer = new Runnable() {
            @Override public void run() {
                try {
                    tvClock.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cb != null && cb.hasPrimaryClip()) {
                        ClipData data = cb.getPrimaryClip();
                        if (data != null && data.getItemCount() > 0) {
                            CharSequence text = data.getItemAt(0).getText();
                            if (text != null) {
                                String raw = text.toString().trim();
                                String preview = raw.length() > 25 ? raw.substring(0, 25) + "..." : raw;
                                tvClipboard.setText("剪贴板预览: " + (raw.isEmpty() ? "无内容" : preview));
                            }
                        }
                    }
                } catch (Exception e) {}
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timer);

        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                tvBattery.setText("电量监控: " + level + "%");
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onDestroy() {
        if (handler != null && timer != null) handler.removeCallbacks(timer);
        if (batteryReceiver != null) { try { unregisterReceiver(batteryReceiver); } catch (Exception e) {} }
        super.onDestroy();
    }
}