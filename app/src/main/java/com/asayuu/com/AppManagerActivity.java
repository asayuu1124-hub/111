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

        etSearch = new EditText(this);
        etSearch.setHint("🔍 输入应用名称或包名搜索...");
        etSearch.setBackgroundColor(Color.WHITE);
        etSearch.setPadding(30, 25, 30, 25);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(-1, -2);
        lpSearch.setMargins(0, 0, 0, 20);
        root.addView(etSearch, lpSearch);

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
                        if (Build.VERSION.SDK_INT >= 21) {
                            item.splitPaths = pi.applicationInfo.splitSourceDirs;
                        }
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
                String badge = (item.splitPaths != null && item.splitPaths.length > 0) ? " [📦 Split-APKs]" : "";
                t1.setText(item.name + badge); t1.setTextColor(Color.BLACK);
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
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XiaoyuBackup");
                    if (!outDir.exists() && !outDir.mkdirs()) {
                        outDir = new File(getExternalFilesDir(null), "Backup");
                        if (!outDir.exists() && !outDir.mkdirs()) {
                            return "致命错误: 您的手机完全锁死了写入权限";
                        }
                    }
                    
                    String safeName = item.name.replaceAll("[\\\\/:*?\"<>|]", "");
                    
                    if (item.splitPaths != null && item.splitPaths.length > 0) {
                        File splitDir = new File(outDir, safeName + "_" + item.pkg + "_APKs");
                        if (!splitDir.exists()) splitDir.mkdirs();
                        
                        copyFile(new File(item.path), new File(splitDir, "base.apk"));
                        for (int i = 0; i < item.splitPaths.length; i++) {
                            copyFile(new File(item.splitPaths[i]), new File(splitDir, "split_" + i + ".apk"));
                        }
                        return "✅ 提取成功 (Split格式)!\n路径: " + splitDir.getAbsolutePath();
                    } else {
                        File dst = new File(outDir, safeName + "_" + item.pkg + ".apk");
                        copyFile(new File(item.path), dst);
                        return "✅ 提取成功!\n路径: " + dst.getAbsolutePath();
                    }
                } catch (Exception e) { 
                    return "❌ 提取失败: " + e.getMessage(); 
                }
            }
            @Override protected void onPostExecute(String resultMsg) {
                Toast.makeText(AppManagerActivity.this, resultMsg, Toast.LENGTH_LONG).show();
            }
        }.execute();
    }
    
    private void copyFile(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192]; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }

    static class AppItem { String name, pkg, path; String[] splitPaths; }
}
