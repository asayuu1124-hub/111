package com.asayuu.com;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class AppManagerActivity extends Activity {
    private ListView listView;
    private ProgressBar loader;
    private EditText etSearch;
    private List<AppItem> originalList = new ArrayList<AppItem>();
    private List<AppItem> appList = new ArrayList<AppItem>();
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EDEDED"));
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("📦 立方应用提取器");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#333333"));
        title.setTextSize(18);
        title.setPadding(0, 20, 0, 20);
        root.addView(title);

        // 🔍 新增：搜索功能输入框
        etSearch = new EditText(this);
        etSearch.setHint("🔍 输入应用名称或包名搜索...");
        etSearch.setBackgroundColor(Color.WHITE);
        etSearch.setPadding(30, 25, 30, 25);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(-1, -2);
        lpSearch.setMargins(0, 0, 0, 20);
        root.addView(etSearch, lpSearch);

        // 🔍 新增：实时搜索过滤逻辑
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterApps(s.toString()); }
        });

        loader = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loader.setIndeterminate(true);
        loader.setVisibility(View.GONE);
        root.addView(loader);

        listView = new ListView(this);
        listView.setDividerHeight(1);
        root.addView(listView, new LinearLayout.LayoutParams(-1, -1));

        setContentView(root);
        loadApps();
    }

    private void filterApps(String keyword) {
        appList.clear();
        if (keyword.trim().isEmpty()) {
            appList.addAll(originalList);
        } else {
            String lower = keyword.toLowerCase();
            for (AppItem item : originalList) {
                if (item.name.toLowerCase().contains(lower) || item.pkg.toLowerCase().contains(lower)) {
                    appList.add(item);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void loadApps() {
        loader.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                for (PackageInfo pi : packages) {
                    if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        AppItem item = new AppItem();
                        item.name = pi.applicationInfo.loadLabel(pm).toString();
                        item.pkg = pi.packageName;
                        item.path = pi.applicationInfo.sourceDir;
                        originalList.add(item);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loader.setVisibility(View.GONE);
                        appList.addAll(originalList);
                        initList();
                    }
                });
            }
        }).start();
    }

    private void initList() {
        adapter = new BaseAdapter() {
            @Override public int getCount() { return appList.size(); }
            @Override public Object getItem(int p) { return appList.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, ViewGroup p3) {
                if (v == null) {
                    v = LayoutInflater.from(AppManagerActivity.this).inflate(android.R.layout.simple_list_item_2, null);
                    v.setPadding(20, 30, 20, 30);
                }
                AppItem item = appList.get(p);
                TextView t1 = (TextView) v.findViewById(android.R.id.text1);
                TextView t2 = (TextView) v.findViewById(android.R.id.text2);
                t1.setText(item.name); t1.setTextColor(Color.BLACK);
                t2.setText(item.pkg); t2.setTextColor(Color.GRAY);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                extractApk(appList.get(p3));
            }
        });
    }

    private void extractApk(final AppItem item) {
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... voids) {
                try {
                    File src = new File(item.path);
                    
                    // 🛠️ 核心修改：双重保险路径写入策略，绕过分区存储限制
                    // 策略1：尝试写入系统的公共 Download 目录 (Android 推荐方式)
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XiaoyuBackup");
                    if (!outDir.exists() && !outDir.mkdirs()) {
                        // 策略2：如果失败，强制写入应用专有的外部目录 (绝对不会被拦截，且无需额外权限)
                        outDir = new File(getExternalFilesDir(null), "Backup");
                        if (!outDir.exists() && !outDir.mkdirs()) {
                            return "致命错误: 您的手机完全锁死了写入权限";
                        }
                    }
                    
                    // 清理应用名称中的特殊字符，避免引起文件系统崩溃
                    String safeName = item.name.replaceAll("[\\\\/:*?\"<>|]", "");
                    File dst = new File(outDir, safeName + "_" + item.pkg + ".apk");
                    
                    InputStream in = new FileInputStream(src);
                    OutputStream out = new FileOutputStream(dst);
                    byte[] buf = new byte[2048]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close(); out.close();
                    
                    return "✅ 提取成功!\n路径: " + dst.getAbsolutePath();
                } catch (Exception e) { 
                    return "❌ 提取失败: " + e.getMessage(); 
                }
            }
            @Override protected void onPostExecute(String resultMsg) {
                Toast.makeText(AppManagerActivity.this, resultMsg, Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    static class AppItem { String name, pkg, path; }
}
