package com.asayuu.com;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

public class LogTerminalActivity extends Activity {
    private ListView listView;
    private LogAdapter adapter;
    private LinkedList<CharSequence> logList = new LinkedList<CharSequence>();
    private Process logcatProcess;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isReading = true;
    private final int MAX_LINES = 1000;
    private String myPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myPid = String.valueOf(android.os.Process.myPid());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));

        TextView title = new TextView(this);
        title.setText("📟 物理設備遙測與日誌裝甲 (PID: " + myPid + ")");
        title.setTextColor(Color.parseColor("#00FF00"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(20, 40, 20, 20);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        root.addView(title);

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.HORIZONTAL);
        controlPanel.setPadding(20, 0, 20, 20);
        
        Button btnClear = new Button(this);
        btnClear.setText("清 空 屏 幕");
        btnClear.setTextColor(Color.WHITE);
        btnClear.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lpBtn.rightMargin = 15;
        controlPanel.addView(btnClear, lpBtn);

        Button btnDump = new Button(this);
        btnDump.setText("快 照 落 盤");
        btnDump.setTextColor(Color.WHITE);
        btnDump.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lpBtn2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        controlPanel.addView(btnDump, lpBtn2);

        root.addView(controlPanel);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        root.addView(listView, new LinearLayout.LayoutParams(-1, -1));

        setContentView(root);

        adapter = new LogAdapter();
        listView.setAdapter(adapter);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logList.clear();
                adapter.notifyDataSetChanged();
            }
        });
        
        btnDump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dumpLogsToExport();
            }
        });

        startLogcat();
    }

    private void startLogcat() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec("logcat -c").waitFor();
                    logcatProcess = Runtime.getRuntime().exec("logcat -v time");
                    BufferedReader br = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()));
                    String line;
                    while (isReading && (line = br.readLine()) != null) {
                        if (line.contains(myPid)) {
                            final CharSequence styledLine = formatLogLine(line);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (logList.size() >= MAX_LINES) logList.removeFirst();
                                    logList.add(styledLine);
                                    adapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    private CharSequence formatLogLine(String line) {
        SpannableString ss = new SpannableString(line);
        if (line.contains(" E/")) {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#FF4444")), 0, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (line.contains(" W/")) {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#FFBB33")), 0, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (line.contains(" I/")) {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#99CC00")), 0, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#AAAAAA")), 0, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    private void dumpLogsToExport() {
        try {
            File dir = new File(android.os.Environment.getExternalStorageDirectory(), "Download/XiaoyuExport");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "Telemetry_Dump_" + System.currentTimeMillis() + ".txt");
            FileOutputStream fos = new FileOutputStream(file);
            for (CharSequence seq : logList) {
                fos.write((seq.toString() + "\n").getBytes("UTF-8"));
            }
            fos.close();
            Toast.makeText(this, "物理快照已匯出: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "匯出失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        isReading = false;
        if (logcatProcess != null) logcatProcess.destroy();
        super.onDestroy();
    }

    private class LogAdapter extends BaseAdapter {
        @Override public int getCount() { return logList.size(); }
        @Override public Object getItem(int position) { return logList.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView == null) {
                tv = new TextView(LogTerminalActivity.this);
                tv.setTextSize(10f);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setPadding(10, 8, 10, 8);
            } else {
                tv = (TextView) convertView;
            }
            tv.setText(logList.get(position));
            return tv;
        }
    }
}