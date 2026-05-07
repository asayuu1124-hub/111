package com.asayuu.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AiChatActivity extends Activity {

    private ListView lvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvStatus, tvTitle, tvModel;
    
    private TextView btnToggleThink, btnToggleSearch;
    
    private LinearLayout layoutDrawer;
    private View viewDrawerDim;
    private ListView lvSessions;
    
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatList = new ArrayList<ChatMessage>();
    
    private SessionAdapter sessionAdapter;
    private List<String[]> sessionList = new ArrayList<String[]>();
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isAiTyping = false;
    
    private DBHelper db;
    private SharedPreferences sp;
    private long currentSessionId = -1;
    private String currentSessionTitle = "";

    private boolean isThinkMode = false;
    private boolean isSearchMode = false;

    private final String[] MODEL_NAMES = {
        "⚡ 極速閃存 (V4-Flash)", 
        "👑 專業旗艦 (V4-Pro)", 
        "⚡ 通用核心 (V3 舊版)", 
        "🧠 深度推理 (R1 舊版)"
    };
    private final String[] MODEL_IDS = {
        "deepseek-v4-flash", 
        "deepseek-v4-pro", 
        "deepseek-chat", 
        "deepseek-reasoner"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);
        
        db = new DBHelper(this);
        sp = getSharedPreferences("asayuu_config", Context.MODE_PRIVATE);
        
        trustAllSSL();

        lvChat = (ListView) findViewById(R.id.lv_chat);
        etInput = (EditText) findViewById(R.id.et_ai_input);
        btnSend = (Button) findViewById(R.id.btn_ai_send);
        tvStatus = (TextView) findViewById(R.id.tv_ai_status);
        tvTitle = (TextView) findViewById(R.id.tv_ai_title); 
        tvModel = (TextView) findViewById(R.id.tv_ai_model); 
        
        btnToggleThink = (TextView) findViewById(R.id.btn_toggle_think);
        btnToggleSearch = (TextView) findViewById(R.id.btn_toggle_search);
        
        layoutDrawer = (LinearLayout) findViewById(R.id.layout_drawer);
        viewDrawerDim = (View) findViewById(R.id.view_drawer_dim);
        lvSessions = (ListView) findViewById(R.id.lv_sessions);

        String savedModel = sp.getString("deepseek_model", "deepseek-v4-flash");
        updateModelUI(savedModel);

        isThinkMode = sp.getBoolean("ai_think_mode", false);
        isSearchMode = sp.getBoolean("ai_search_mode", false);
        updateToggleUI();

        findViewById(R.id.btn_ai_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_ai_menu).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        });
        viewDrawerDim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });

        tvTitle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentSessionId != -1) showRenameDialog(currentSessionId, currentSessionTitle);
            }
        });
        
        tvStatus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showApiKeyDialog(); }
        });

        tvModel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showModelSelectDialog(); }
        });

        btnToggleThink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isThinkMode = !isThinkMode;
                sp.edit().putBoolean("ai_think_mode", isThinkMode).apply();
                updateToggleUI();
            }
        });

        btnToggleSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSearchMode = !isSearchMode;
                sp.edit().putBoolean("ai_search_mode", isSearchMode).apply();
                updateToggleUI();
            }
        });

        chatAdapter = new ChatAdapter();
        lvChat.setAdapter(chatAdapter);
        sessionAdapter = new SessionAdapter();
        lvSessions.setAdapter(sessionAdapter);

        initSession();

        findViewById(R.id.btn_new_session).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewSession();
                closeDrawer();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAiTyping) {
                    Toast.makeText(AiChatActivity.this, "請等待 AI 回覆完畢", Toast.LENGTH_SHORT).show();
                    return;
                }
                String apiKey = sp.getString("deepseek_api_key", "");
                if (apiKey.isEmpty()) {
                    Toast.makeText(AiChatActivity.this, "請先點擊右上角設定 API Key", Toast.LENGTH_LONG).show();
                    showApiKeyDialog();
                    return;
                }
                
                String text = etInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    addMessage("user", text, true);
                    etInput.setText("");
                    callDeepSeekAPI(apiKey);
                }
            }
        });
        
        lvSessions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long clickedSessionId = Long.parseLong(sessionList.get(position)[0]);
                if (clickedSessionId != currentSessionId) {
                    currentSessionId = clickedSessionId;
                    loadChatHistoryForCurrentSession();
                }
                closeDrawer();
            }
        });

        lvSessions.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final long targetId = Long.parseLong(sessionList.get(position)[0]);
                final String targetTitle = sessionList.get(position)[1];
                String[] options = {"📝 重新命名", "🗑 刪除鏈路"};
                new AlertDialog.Builder(AiChatActivity.this)
                    .setTitle("管理神經鏈路")
                    .setItems(options, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) showRenameDialog(targetId, targetTitle);
                            else confirmDeleteSession(targetId);
                        }
                    }).show();
                return true;
            }
        });
    }

    private void updateToggleUI() {
        if (isThinkMode) {
            btnToggleThink.setText("🟢 深度思考");
            btnToggleThink.setTextColor(0xFF8E44AD); 
        } else {
            btnToggleThink.setText("⚪ 深度思考");
            btnToggleThink.setTextColor(0xFF888888); 
        }

        if (isSearchMode) {
            btnToggleSearch.setText("🟢 聯網模式");
            btnToggleSearch.setTextColor(0xFF2980B9); 
        } else {
            btnToggleSearch.setText("⚪ 聯網模式");
            btnToggleSearch.setTextColor(0xFF888888); 
        }
    }

    private void updateModelUI(String modelId) {
        if (modelId.equals("deepseek-v4-pro")) tvModel.setText("Pro ▼");
        else if (modelId.equals("deepseek-chat")) tvModel.setText("V3 ▼");
        else if (modelId.equals("deepseek-reasoner")) tvModel.setText("R1 ▼");
        else tvModel.setText("Flash ▼");
    }

    private void showModelSelectDialog() {
        String currentModel = sp.getString("deepseek_model", "deepseek-v4-flash");
        int checkedItem = 0;
        for (int i = 0; i < MODEL_IDS.length; i++) {
            if (MODEL_IDS[i].equals(currentModel)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("切換神經核心 (V4 世代)")
            .setSingleChoiceItems(MODEL_NAMES, checkedItem, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sp.edit().putString("deepseek_model", MODEL_IDS[which]).apply();
                    updateModelUI(MODEL_IDS[which]);
                    Toast.makeText(AiChatActivity.this, "已切換至: " + MODEL_NAMES[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // --- 🌐 原生網路與流式解析引擎 (SSE) ---
    private void callDeepSeekAPI(final String apiKey) {
        isAiTyping = true;
        tvStatus.setText("● 運算中...");
        tvStatus.setTextColor(0xFFE74C3C);

        final ChatMessage aiMsg = new ChatMessage("ai", "");
        chatList.add(aiMsg);
        chatAdapter.notifyDataSetChanged();
        lvChat.setSelection(chatList.size() - 1);
        
        final long reqSessionId = currentSessionId;
        final String selectedModel = sp.getString("deepseek_model", "deepseek-v4-flash");

        String currentTime = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss (EEEE)", Locale.getDefault()).format(new Date());
        int batteryPct = -1;
        try {
            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                batteryPct = (int) (level * 100 / (float) scale);
            }
        } catch (Exception e) {}

        long availRam = 0, totalRam = 0;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                availRam = mi.availMem / 1048576L;
                totalRam = mi.totalMem / 1048576L;
            }
        } catch (Exception e) {}

        final StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是一個運行在移動端極客應用「小欲」中的終端神經網路。請保持專業、精簡的駭客風格回答。\n\n");
        sysPrompt.append("【當前宿主設備物理狀態】\n");
        sysPrompt.append("- 本地系統精確時間: ").append(currentTime).append("\n");
        if (batteryPct != -1) sysPrompt.append("- 物理電池剩餘電量: ").append(batteryPct).append("%\n");
        if (totalRam > 0) sysPrompt.append("- 記憶體(RAM)狀態: 剩餘 ").append(availRam).append("MB / 總共 ").append(totalRam).append("MB\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                try {
                    URL url = new URL("https://api.deepseek.com/chat/completions");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(120000); 

                    JSONObject payload = new JSONObject();
                    payload.put("model", selectedModel); 
                    payload.put("stream", true); 
                    
                    if (isSearchMode) {
                        payload.put("search", true); 
                        payload.put("net", true);
                    }
                    
                    // ⭐ 核心修復：V4 API 要求 thinking 必須是物件
                    if (isThinkMode) {
                        JSONObject thinkObj = new JSONObject();
                        thinkObj.put("type", "enabled");
                        thinkObj.put("budget_tokens", 4096); 
                        payload.put("thinking", thinkObj);
                    }

                    JSONArray messages = new JSONArray();
                    
                    JSONObject systemMsg = new JSONObject();
                    systemMsg.put("role", "system");
                    systemMsg.put("content", sysPrompt.toString());
                    messages.put(systemMsg);

                    List<String[]> history = db.getChatHistoryBySession(reqSessionId);
                    for (String[] h : history) {
                        JSONObject m = new JSONObject();
                        m.put("role", h[0].equals("ai") ? "assistant" : "user");
                        m.put("content", h[1]);
                        messages.put(m);
                    }
                    payload.put("messages", messages);

                    OutputStream os = conn.getOutputStream();
                    os.write(payload.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    final int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if (data.equals("[DONE]")) break; 
                                
                                try {
                                    JSONObject chunk = new JSONObject(data);
                                    JSONArray choices = chunk.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                        
                                        final StringBuilder chunkContent = new StringBuilder();
                                        if (delta != null) {
                                            if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                                String rc = delta.optString("reasoning_content", "");
                                                if (!rc.isEmpty() && !rc.equals("null")) {
                                                    if (aiMsg.content.isEmpty() || !aiMsg.content.contains("💡 深度推演中...")) {
                                                        chunkContent.append("💡 深度推演中...\n");
                                                    }
                                                    chunkContent.append(rc);
                                                }
                                            }
                                            
                                            if (delta.has("content") && !delta.isNull("content")) {
                                                String cStr = delta.optString("content", "");
                                                if (!cStr.isEmpty() && !cStr.equals("null")) {
                                                    if (!aiMsg.content.contains("───\n") && aiMsg.content.contains("💡 深度推演中...")) {
                                                        chunkContent.append("\n\n───\n");
                                                    }
                                                    chunkContent.append(cStr);
                                                }
                                            }
                                            
                                            if (chunkContent.length() > 0) {
                                                mainHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        aiMsg.content += chunkContent.toString();
                                                        chatAdapter.notifyDataSetChanged();
                                                        lvChat.setSelection(chatList.size() - 1);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    } else {
                        final String errorBody = readErrorStream(conn);
                        mainHandler.post(new Runnable() {
                            @Override public void run() { aiMsg.content += "\n[API 拒絕連線: 狀態碼 " + responseCode + "]\n" + errorBody; }
                        });
                    }
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override public void run() { aiMsg.content += "\n[網路引擎崩潰: " + e.getMessage() + "]"; }
                    });
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception e) {}
                    if (conn != null) conn.disconnect();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isAiTyping = false;
                            tvStatus.setText("● 待命");
                            tvStatus.setTextColor(0xFF27AE60);
                            chatAdapter.notifyDataSetChanged();
                            if (!aiMsg.content.isEmpty()) {
                                db.addChatMessage(reqSessionId, "ai", aiMsg.content);
                                refreshSessionList();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private void trustAllSSL() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) { return true; }
            });
        } catch (Exception e) {}
    }

    // --- UI 與本地邏輯 ---

    private void showApiKeyDialog() {
        final EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setText(sp.getString("deepseek_api_key", ""));
        input.setBackgroundResource(R.drawable.nm_input_inset);
        input.setPadding(30, 30, 30, 30);
        input.setTextSize(14f);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(50, 30, 50, 0);
        container.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
            .setTitle("⚙️ 部署 API 密鑰")
            .setMessage("請輸入您的 DeepSeek API Key：")
            .setView(container)
            .setPositiveButton("儲存", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sp.edit().putString("deepseek_api_key", input.getText().toString().trim()).apply();
                    Toast.makeText(AiChatActivity.this, "密鑰已裝載", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void showRenameDialog(final long sessionId, String oldTitle) {
        final EditText input = new EditText(this);
        input.setText(oldTitle);
        input.setSelection(oldTitle.length());
        input.setBackgroundResource(R.drawable.nm_input_inset);
        input.setPadding(30, 30, 30, 30);
        input.setTextSize(14f);
        input.setSingleLine(true);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(50, 30, 50, 0);
        container.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
            .setTitle("📝 修改鏈路名稱")
            .setView(container)
            .setPositiveButton("儲存", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        db.updateSessionTitle(sessionId, newTitle);
                        refreshSessionList();
                        if (sessionId == currentSessionId) {
                            currentSessionTitle = newTitle;
                            tvTitle.setText("🧠 " + currentSessionTitle + " ✎");
                        }
                    }
                }
            }).setNegativeButton("取消", null).show();
    }

    private void confirmDeleteSession(final long targetId) {
        new AlertDialog.Builder(this)
            .setTitle("銷毀鏈路")
            .setMessage("確定要永久刪除此對話嗎？")
            .setPositiveButton("刪除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    db.deleteSession(targetId);
                    refreshSessionList();
                    if (targetId == currentSessionId) createNewSession();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void initSession() {
        refreshSessionList();
        if (sessionList.isEmpty()) createNewSession();
        else {
            currentSessionId = Long.parseLong(sessionList.get(0)[0]);
            loadChatHistoryForCurrentSession();
        }
    }

    private void createNewSession() {
        String timeStr = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date());
        currentSessionTitle = "新神經鏈路 " + timeStr;
        currentSessionId = db.createAiSession(currentSessionTitle);
        refreshSessionList();
        
        tvTitle.setText("🧠 " + currentSessionTitle + " ✎");
        chatList.clear();
        chatAdapter.notifyDataSetChanged();
    }

    private void refreshSessionList() {
        sessionList.clear();
        sessionList.addAll(db.getAllSessions());
        sessionAdapter.notifyDataSetChanged();
    }

    private void loadChatHistoryForCurrentSession() {
        for (String[] s : sessionList) {
            if (Long.parseLong(s[0]) == currentSessionId) {
                currentSessionTitle = s[1];
                tvTitle.setText("🧠 " + currentSessionTitle + " ✎");
                break;
            }
        }
        chatList.clear();
        List<String[]> history = db.getChatHistoryBySession(currentSessionId);
        for (String[] msg : history) {
            chatList.add(new ChatMessage(msg[0], msg[1]));
        }
        chatAdapter.notifyDataSetChanged();
        if (chatList.size() > 0) lvChat.setSelection(chatList.size() - 1);
    }

    private void addMessage(String role, String content, boolean saveToDb) {
        chatList.add(new ChatMessage(role, content));
        if (saveToDb) {
            db.addChatMessage(currentSessionId, role, content);
            refreshSessionList(); 
        }
        chatAdapter.notifyDataSetChanged();
        lvChat.setSelection(chatList.size() - 1);
    }

    private void openDrawer() { refreshSessionList(); layoutDrawer.setVisibility(View.VISIBLE); }
    private void closeDrawer() { layoutDrawer.setVisibility(View.GONE); }

    // --- 資料結構與適配器 ---
    private class ChatMessage {
        String role, content;
        public ChatMessage(String role, String content) { this.role = role; this.content = content; }
    }

    private class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return chatList.size(); }
        @Override public Object getItem(int position) { return chatList.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(AiChatActivity.this).inflate(R.layout.item_chat_message, parent, false);
            LinearLayout layoutAi = (LinearLayout) convertView.findViewById(R.id.layout_ai_msg);
            LinearLayout layoutUser = (LinearLayout) convertView.findViewById(R.id.layout_user_msg);
            TextView tvAi = (TextView) convertView.findViewById(R.id.tv_ai_text);
            TextView tvUser = (TextView) convertView.findViewById(R.id.tv_user_text);

            ChatMessage msg = chatList.get(position);
            if (msg.role.equals("ai")) {
                layoutAi.setVisibility(View.VISIBLE); layoutUser.setVisibility(View.GONE); tvAi.setText(msg.content);
            } else {
                layoutAi.setVisibility(View.GONE); layoutUser.setVisibility(View.VISIBLE); tvUser.setText(msg.content);
            }
            return convertView;
        }
    }
    
    private class SessionAdapter extends BaseAdapter {
        @Override public int getCount() { return sessionList.size(); }
        @Override public Object getItem(int position) { return sessionList.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new TextView(AiChatActivity.this);
                ((TextView) convertView).setPadding(40, 40, 40, 40);
                ((TextView) convertView).setTextSize(14f);
            }
            TextView tv = (TextView) convertView;
            String[] session = sessionList.get(position);
            long id = Long.parseLong(session[0]);
            if (id == currentSessionId) {
                tv.setText("▶ " + session[1]); tv.setTextColor(0xFF4A90E2); tv.setBackgroundColor(0xFFE0E0E0);
            } else {
                tv.setText("💬 " + session[1]); tv.setTextColor(0xFF555555); tv.setBackgroundColor(0x00000000);
            }
            return tv;
        }
    }
}
