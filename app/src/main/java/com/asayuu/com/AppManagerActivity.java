package com.asayuu.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class AppManagerActivity extends Activity {

    private LinearLayout rootLayout;
    private ListView lvApps;
    private ProgressBar pbLoading;
    private TextView tvStatus;
    
    private List<AppItem> appList = new ArrayList<AppItem>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#EDEDED"));
        rootLayout.setPadding(30, 40, 30, 30);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("📦 APK 深度解剖与物理提取中枢");
        tvHeader.setTextColor(Color.parseColor("#333333"));
        tvHeader.setTextSize(18f);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeader.setGravity(Gravity.CENTER);
        rootLayout.addView(tvHeader);

        tvStatus = new TextView(this);
        tvStatus.setText("底层探针已就绪，正在进行全域扫描...");
        tvStatus.setTextColor(Color.parseColor("#888888"));
        tvStatus.setTextSize(12f);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 10, 0, 20);
        rootLayout.addView(tvStatus);

        pbLoading = new ProgressBar(this);
        rootLayout.addView(pbLoading);

        lvApps = new ListView(this);
        lvApps.setDividerHeight(2);
        LinearLayout.LayoutParams lvLp = new LinearLayout.LayoutParams(-1, -1);
        lvLp.topMargin = 20;
        rootLayout.addView(lvApps, lvLp);

        setContentView(rootLayout);

        adapter = new AppAdapter(this, appList);
        lvApps.setAdapter(adapter);

        lvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showAppXRayDialog(appList.get(position));
            }
        });

        new ScanAppsTask().execute();
    }

    private class AppItem {
        String appName;
        String packageName;
        String versionName;
        String sourceDir;
        String[] permissions;
        boolean isSystem;
    }

    private class AppAdapter extends ArrayAdapter<AppItem> {
        public AppAdapter(Context context, List<AppItem> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout;
            TextView tvName, tvPkg;
            if (convertView == null) {
                layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(20, 25, 20, 25);

                tvName = new TextView(getContext());
                tvName.setTextColor(Color.parseColor("#333333"));
                tvName.setTextSize(16f);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                
                tvPkg = new TextView(getContext());
                tvPkg.setTextColor(Color.parseColor("#666666"));
                tvPkg.setTextSize(12f);
                
                layout.addView(tvName);
                layout.addView(tvPkg);
            } else {
                layout = (LinearLayout) convertView;
                tvName = (TextView) layout.getChildAt(0);
                tvPkg = (TextView) layout.getChildAt(1);
            }

            AppItem item = getItem(position);
            if (item != null) {
                tvName.setText((item.isSystem ? "⚙️ " : "📱 ") + item.appName);
                tvPkg.setText(item.packageName + " | v" + item.versionName);
            }
            return layout;
        }
    }

    private class ScanAppsTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            
            for (PackageInfo pkgInfo : packages) {
                AppItem item = new AppItem();
                item.appName = pkgInfo.applicationInfo.loadLabel(pm).toString();
                item.packageName = pkgInfo.packageName;
                item.versionName = pkgInfo.versionName == null ? "未知" : pkgInfo.versionName;
                item.sourceDir = pkgInfo.applicationInfo.sourceDir;
                item.permissions = pkgInfo.requestedPermissions;
                item.isSystem = (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                appList.add(item);
            }
            
            Collections.sort(appList, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem a, AppItem b) {
                    if (a.isSystem && !b.isSystem) return 1;
                    if (!a.isSystem && b.isSystem) return -1;
                    return a.appName.compareToIgnoreCase(b.appName);
                }
            });
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            pbLoading.setVisibility(View.GONE);
            tvStatus.setText("扫描完毕，共发现 " + appList.size() + " 个活动程序。点击进行深度解剖。");
            adapter.notifyDataSetChanged();
        }
    }

    private String getPermissionLabel(String perm, PackageManager pm) {
        try {
            PermissionInfo pi = pm.getPermissionInfo(perm, 0);
            CharSequence label = pi.loadLabel(pm);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception e) {}

        if (perm.endsWith("CAMERA")) return "拍摄照片和录制视频";
        if (perm.endsWith("RECORD_AUDIO")) return "录音权限";
        if (perm.endsWith("READ_CONTACTS")) return "读取通讯录联系人";
        if (perm.endsWith("WRITE_CONTACTS")) return "修改通讯录联系人";
        if (perm.endsWith("ACCESS_FINE_LOCATION")) return "获取精准GPS定位";
        if (perm.endsWith("ACCESS_COARSE_LOCATION")) return "获取基站/Wi-Fi大致定位";
        if (perm.endsWith("READ_SMS")) return "读取短信内容";
        if (perm.endsWith("SEND_SMS")) return "发送短信";
        if (perm.endsWith("READ_EXTERNAL_STORAGE")) return "读取存储卡空间";
        if (perm.endsWith("WRITE_EXTERNAL_STORAGE")) return "修改存储卡空间";
        if (perm.endsWith("SYSTEM_ALERT_WINDOW")) return "悬浮窗/在其他应用上层显示";
        if (perm.endsWith("READ_PHONE_STATE")) return "读取设备识别码与通话状态";
        if (perm.endsWith("INTERNET")) return "完全的网络访问权限";
        if (perm.endsWith("VIBRATE")) return "控制手机振动马达";
        if (perm.endsWith("WAKE_LOCK")) return "防止手机进入休眠状态";

        return "系统级未定权限";
    }

    private void showAppXRayDialog(final AppItem item) {
        final Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 50, 40, 50);
        layout.setBackgroundColor(Color.parseColor("#FFFFFF"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText((item.isSystem ? "⚙️ " : "📱 ") + item.appName);
        tvTitle.setTextColor(Color.parseColor("#2C3E50"));
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        TextView tvPath = new TextView(this);
        tvPath.setText("物理扇区路径:\n" + item.sourceDir);
        tvPath.setTextColor(Color.parseColor("#E67E22"));
        tvPath.setTextSize(11f);
        tvPath.setPadding(0, 15, 0, 20);
        layout.addView(tvPath);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.4));
        scroll.setBackgroundResource(R.drawable.nm_card_inset);
        
        TextView tvPerms = new TextView(this);
        tvPerms.setPadding(20, 20, 20, 20);
        tvPerms.setTextColor(Color.parseColor("#555555"));
        tvPerms.setTextSize(12f);
        
        StringBuilder permsStr = new StringBuilder("【底层权限嗅探结果】\n\n");
        PackageManager pm = getPackageManager();
        
        if (item.permissions != null && item.permissions.length > 0) {
            // 双缓冲重构：物理隔离高危与普通权限流
            StringBuilder dangerousStr = new StringBuilder();
            StringBuilder normalStr = new StringBuilder();
            
            for (String perm : item.permissions) {
                String label = getPermissionLabel(perm, pm);
                if (perm.contains("LOCATION") || perm.contains("RECORD_AUDIO") || perm.contains("CAMERA") || perm.contains("READ_SMS") || perm.contains("CONTACTS")) {
                    dangerousStr.append("⚠️ [").append(label).append("]\n   原码: ").append(perm).append("\n\n");
                } else {
                    normalStr.append("🔹 [").append(label).append("]\n   原码: ").append(perm).append("\n\n");
                }
            }
            // 顶层注入高危行为
            permsStr.append(dangerousStr.toString());
            // 衔接常规行为
            permsStr.append(normalStr.toString());
        } else {
            permsStr.append("该程序处于安全沙盒中，未声明任何敏感权限。");
        }
        tvPerms.setText(permsStr.toString());
        scroll.addView(tvPerms);
        layout.addView(scroll, scrollLp);

        Button btnDirectExtract = new Button(this);
        btnDirectExtract.setText("📥 直接导出原生 APK (明文)");
        btnDirectExtract.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnDirectExtract.setTextColor(Color.parseColor("#27AE60")); 
        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(-1, 140);
        btnLp1.topMargin = 30;
        layout.addView(btnDirectExtract, btnLp1);

        Button btnVaultExtract = new Button(this);
        btnVaultExtract.setText("🗃️ 物理剥离并推入暗盒 (密文)");
        btnVaultExtract.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnVaultExtract.setTextColor(Color.parseColor("#8E44AD")); 
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(-1, 140);
        btnLp2.topMargin = 20;
        layout.addView(btnVaultExtract, btnLp2);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setAttributes(lp);
        }

        btnDirectExtract.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                executeDirectExtraction(item);
            }
        });

        btnVaultExtract.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                executeApkExtraction(item);
            }
        });

        dialog.show();
    }

    private void executeDirectExtraction(final AppItem item) {
        new AsyncTask<Void, Void, Boolean>() {
            Dialog loadingDialog;
            File outFile;

            @Override
            protected void onPreExecute() {
                loadingDialog = new Dialog(AppManagerActivity.this);
                loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                loadingDialog.setCancelable(false);
                LinearLayout layout = new LinearLayout(AppManagerActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 50, 50, 50);
                layout.setGravity(Gravity.CENTER);
                layout.setBackgroundColor(Color.WHITE);
                layout.addView(new ProgressBar(AppManagerActivity.this));
                
                TextView tv = new TextView(AppManagerActivity.this);
                tv.setText("正在执行原生明文剥离...\n目标路径: Download 目录");
                tv.setTextColor(Color.parseColor("#333333"));
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, 30, 0, 0);
                layout.addView(tv);
                loadingDialog.setContentView(layout);
                loadingDialog.show();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    File sourceApk = new File(item.sourceDir);
                    if (!sourceApk.exists()) return false;

                    File exportDir = new File(Environment.getExternalStorageDirectory(), "Download");
                    if (!exportDir.exists()) exportDir.mkdirs();
                    
                    outFile = new File(exportDir, item.appName + "_" + item.versionName + ".apk");

                    Source source = Okio.source(new FileInputStream(sourceApk));
                    Sink sink = Okio.sink(new FileOutputStream(outFile));
                    BufferedSource bufferedSource = Okio.buffer(source);
                    BufferedSink bufferedSink = Okio.buffer(sink);

                    bufferedSink.writeAll(bufferedSource);

                    bufferedSink.flush();
                    bufferedSink.close();
                    bufferedSource.close();
                    return true;

                } catch (Exception e) {
                    if (outFile != null && outFile.exists()) outFile.delete();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
                if (success) {
                    new AlertDialog.Builder(AppManagerActivity.this)
                        .setTitle("剥离成功")
                        .setMessage("目标程序的原生 APK 已成功抽离，处于非加密明文状态！\n\n存放路径：\nDownload/" + outFile.getName())
                        .setPositiveButton("确定", null)
                        .show();
                } else {
                    Toast.makeText(AppManagerActivity.this, "❌ 提取失败：物理扇区读取受限或磁盘空间不足。", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void executeApkExtraction(final AppItem item) {
        new AsyncTask<Void, Void, Boolean>() {
            Dialog loadingDialog;
            File outFile;

            @Override
            protected void onPreExecute() {
                loadingDialog = new Dialog(AppManagerActivity.this);
                loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                loadingDialog.setCancelable(false);
                LinearLayout layout = new LinearLayout(AppManagerActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 50, 50, 50);
                layout.setGravity(Gravity.CENTER);
                layout.setBackgroundColor(Color.WHITE);
                layout.addView(new ProgressBar(AppManagerActivity.this));
                
                TextView tv = new TextView(AppManagerActivity.this);
                tv.setText("正在执行物理剥离与 AES 盲收敛...\n请勿切断电源或退出应用。");
                tv.setTextColor(Color.parseColor("#333333"));
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, 30, 0, 0);
                layout.addView(tv);
                loadingDialog.setContentView(layout);
                loadingDialog.show();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    File sourceApk = new File(item.sourceDir);
                    if (!sourceApk.exists()) return false;

                    File vaultDir = new File(Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault/Agent");
                    if (!vaultDir.exists()) vaultDir.mkdirs();
                    
                    outFile = new File(vaultDir, item.appName + "_" + item.versionName + ".apk.snt");

                    String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                    if (deviceId == null) deviceId = "xiaoyu_fallback_id";
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] keyBytes = digest.digest((deviceId + "_xiaoyu_agent_master_key").getBytes("UTF-8"));
                    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    byte[] iv = new byte[16];
                    new SecureRandom().nextBytes(iv);
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(iv);
                    CipherOutputStream cos = new CipherOutputStream(fos, cipher);

                    Source source = Okio.source(new FileInputStream(sourceApk));
                    Sink sink = Okio.sink(cos);
                    BufferedSource bufferedSource = Okio.buffer(source);
                    BufferedSink bufferedSink = Okio.buffer(sink);

                    bufferedSink.writeAll(bufferedSource);

                    bufferedSink.flush();
                    bufferedSink.close();
                    bufferedSource.close();
                    return true;

                } catch (Exception e) {
                    if (outFile != null && outFile.exists()) outFile.delete();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
                if (success) {
                    new AlertDialog.Builder(AppManagerActivity.this)
                        .setTitle("提取成功")
                        .setMessage(item.appName + " 的底层 APK 已被成功剥离并物理加密。\n\n该数据现已收敛至暗盒的 [终端] 标签下。您可以前往 SafeBox 将其解密导出或销毁。")
                        .setPositiveButton("确定", null)
                        .show();
                } else {
                    Toast.makeText(AppManagerActivity.this, "❌ 剥离失败：物理扇区读取受限或磁盘空间不足。", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }
}
