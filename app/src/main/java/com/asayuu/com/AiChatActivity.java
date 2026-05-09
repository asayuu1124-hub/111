package com.asayuu.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AiChatActivity extends Activity {

    private ListView lvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvStatus, tvTitle, tvModel;
    
    private TextView btnToggleThink, btnToggleSearch;
    private TextView btnToggleAgent;
    
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
    private boolean isAgentMode = false;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final String[] MODEL_NAMES = {
        "⚡ 极速闪存 (V4-Flash)", 
        "👑 专业旗舰 (V4-Pro)", 
        "⚡ 通用核心 (V3 旧版)", 
        "🧠 深度推理 (R1 旧版)"
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

        btnToggleAgent = new TextView(this);
        btnToggleAgent.setText("⚪ 工作流");
        btnToggleAgent.setTextColor(0xFF888888);
        btnToggleAgent.setPadding(20, 0, 20, 0);
        btnToggleAgent.setTextSize(14f);
        try {
            ViewGroup toggleContainer = (ViewGroup) btnToggleThink.getParent();
            toggleContainer.addView(btnToggleAgent);
        } catch (Exception e) {}

        String savedModel = sp.getString("deepseek_model", "deepseek-v4-flash");
        updateModelUI(savedModel);

        isThinkMode = sp.getBoolean("ai_think_mode", false);
        isSearchMode = sp.getBoolean("ai_search_mode", false);
        isAgentMode = sp.getBoolean("ai_agent_mode", false);
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

        btnToggleAgent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isAgentMode = !isAgentMode;
                sp.edit().putBoolean("ai_agent_mode", isAgentMode).apply();
                updateToggleUI();
                if (isAgentMode) Toast.makeText(AiChatActivity.this, "已切换至 Agent 状态机", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(AiChatActivity.this, "请等待任务回复完毕", Toast.LENGTH_SHORT).show();
                    return;
                }
                String apiKey = sp.getString("deepseek_api_key", "");
                if (apiKey.isEmpty()) {
                    Toast.makeText(AiChatActivity.this, "请先点击右上角设定 API Key", Toast.LENGTH_LONG).show();
                    showApiKeyDialog();
                    return;
                }
                
                String text = etInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    if (checkLocalIntentInterception(text)) {
                        etInput.setText("");
                        return;
                    }

                    addMessage("user", text, true);
                    etInput.setText("");
                    
                    if (isAgentMode) {
                        callAgentWorkflowAPI(apiKey, text);
                    } else {
                        callDeepSeekAPI(apiKey);
                    }
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
                String[] options = {"📝 重新命名", "🗑 删除链路"};
                new AlertDialog.Builder(AiChatActivity.this)
                    .setTitle("管理神经链路")
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

    @Override
    protected void onPause() {
        super.onPause();
        saveSlidingWindow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreSlidingWindow();
    }

    private void saveSlidingWindow() {
        if (chatList.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            int start = Math.max(0, chatList.size() - 40); 
            for (int i = start; i < chatList.size(); i++) {
                ChatMessage msg = chatList.get(i);
                JSONObject obj = new JSONObject();
                obj.put("role", msg.role);
                obj.put("content", msg.toRawString());
                arr.put(obj);
            }
            
            File cacheFile = new File(getExternalFilesDir(null), ".chat_cache_v9");
            
            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            if (deviceId == null) deviceId = "xiaoyu_fallback_id";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest((deviceId + "_xiaoyu_sliding_window").getBytes("UTF-8"));
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(iv);
            CipherOutputStream cos = new CipherOutputStream(fos, cipher);
            cos.write(arr.toString().getBytes("UTF-8"));
            cos.flush();
            cos.close();
        } catch (Exception e) {}
    }

    private void restoreSlidingWindow() {
        try {
            File cacheFile = new File(getExternalFilesDir(null), ".chat_cache_v9");
            if (!cacheFile.exists()) return;
            
            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            if (deviceId == null) deviceId = "xiaoyu_fallback_id";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest((deviceId + "_xiaoyu_sliding_window").getBytes("UTF-8"));
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            
            FileInputStream fis = new FileInputStream(cacheFile);
            byte[] iv = new byte[16];
            fis.read(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int d;
            javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(fis, cipher);
            while ((d = cis.read(b)) != -1) {
                baos.write(b, 0, d);
            }
            cis.close();
            
            if (chatList.isEmpty()) {
                String jsonStr = new String(baos.toByteArray(), "UTF-8");
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    chatList.add(new ChatMessage(obj.getString("role"), obj.getString("content")));
                }
                chatAdapter.notifyDataSetChanged();
                if (chatList.size() > 0) lvChat.setSelection(chatList.size() - 1);
            }
            
            cacheFile.delete();
        } catch (Exception e) {}
    }

    private boolean checkLocalIntentInterception(final String text) {
        if (text.matches(".*(打开|启动|进入).*(雷达|声呐|局域网|探针).*")) {
            interceptAndLaunch("检测到局域网指令，正在强行打通物理雷达链路...", LanRadarActivity.class);
            return true;
        } else if (text.matches(".*(打开|启动|进入).*(暗盒|私密|保险箱).*")) {
            interceptAndLaunch("检测到暗盒召唤指令，正在下潜至物理安全层...", SafeBoxActivity.class);
            return true;
        } else if (text.matches(".*(打开|启动|进入).*(提取|立方|应用).*")) {
            interceptAndLaunch("检测到提取指令，正在挂载立方提取器...", AppManagerActivity.class);
            return true;
        } else if (text.matches(".*(打开|启动|测速|嗅探|温控).*(悬浮窗|前台).*")) {
            interceptAndLaunch("检测到防窥与测速锁定指令，注入悬浮装甲中...", null); 
            return true;
        } else if (text.matches(".*(定时|守护|后台|唤醒|静默).*(任务|工作流|Agent|巡检).*")) {
            interceptAndLaunch("检测到定时守护指令，已触发绝对前台保活机制，AlarmManager 将于 10 秒后执行后台巡检与盲收敛...", AgentService.class);
            return true;
        }
        return false;
    }

    private void interceptAndLaunch(final String msgStr, final Class<?> targetActivity) {
        addMessage("user", etInput.getText().toString().trim(), true);
        final ChatMessage aiMsg = new ChatMessage("ai", "");
        chatList.add(aiMsg);
        chatAdapter.notifyDataSetChanged();
        lvChat.setSelection(chatList.size() - 1);
        
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                aiMsg.finalContent = "⚡ [本地意图物理拦截]\n" + msgStr;
                aiMsg.invalidateCache();
                chatAdapter.notifyDataSetChanged();
                db.addChatMessage(currentSessionId, "ai", aiMsg.toRawString());
                refreshSessionList();
                
                if (targetActivity != null) {
                    if (android.app.Service.class.isAssignableFrom(targetActivity)) {
                        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        Intent intent = new Intent(AiChatActivity.this, targetActivity);
                        int flags = 134217728; // FLAG_UPDATE_CURRENT
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            flags |= 67108864; // FLAG_IMMUTABLE
                        }
                        android.app.PendingIntent pi = android.app.PendingIntent.getService(AiChatActivity.this, 0, intent, flags);
                        
                        long triggerTime = System.currentTimeMillis() + 10000;
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pi);
                        } else if (android.os.Build.VERSION.SDK_INT >= 19) {
                            am.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pi);
                        } else {
                            am.set(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pi);
                        }
                    } else {
                        startActivity(new Intent(AiChatActivity.this, targetActivity));
                    }
                } else {
                    startService(new Intent(AiChatActivity.this, FloatingService.class));
                }
            }
        }, 300);
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
            btnToggleSearch.setText("🟢 联网模式");
            btnToggleSearch.setTextColor(0xFF2980B9); 
        } else {
            btnToggleSearch.setText("⚪ 联网模式");
            btnToggleSearch.setTextColor(0xFF888888); 
        }

        if (isAgentMode) {
            btnToggleAgent.setText("🟢 工作流");
            btnToggleAgent.setTextColor(0xFFE67E22); 
        } else {
            btnToggleAgent.setText("⚪ 工作流");
            btnToggleAgent.setTextColor(0xFF888888); 
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
            .setTitle("切换神经核心 (V4 世代)")
            .setSingleChoiceItems(MODEL_NAMES, checkedItem, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sp.edit().putString("deepseek_model", MODEL_IDS[which]).apply();
                    updateModelUI(MODEL_IDS[which]);
                    Toast.makeText(AiChatActivity.this, "已切换至: " + MODEL_NAMES[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void callAgentWorkflowAPI(final String apiKey, final String task) {
        isAiTyping = true;
        tvStatus.setText("● Agent 运行中...");
        tvStatus.setTextColor(0xFFE67E22);

        final ChatMessage aiMsg = new ChatMessage("ai", "");
        chatList.add(aiMsg);
        chatAdapter.notifyDataSetChanged();
        lvChat.setSelection(chatList.size() - 1);
        
        final long reqSessionId = currentSessionId;
        final String selectedModel = sp.getString("deepseek_model", "deepseek-v4-flash");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int step = 0;
                    boolean finished = false;
                    JSONArray messages = new JSONArray();
                    
                    String prompt = "你是一个运行在原生Android端的工作流Agent。你具备以下实体权限：\n" +
                        "A. TEXT_PROCESS (纯文本深度处理、翻译、总结)\n" +
                        "B. READ_DEVICE (读取底层设备精确时间、RAM、电量状态)\n" +
                        "C. ENCRYPT_SAVE (将指定内容进行AES物理加密并写入暗盒目录，必填参数: content)\n" +
                        "D. SCRAPE_WEB (发起HTTP网络嗅探获取目标网页文本，必填参数: url)\n" +
                        "E. FILE_READ (读取本地绝对路径下的纯文本文件，必填参数: param为文件绝对路径)\n" +
                        "F. CLIPBOARD_MANAGE (将内容静默写入宿主机剪贴板，必填参数: content)\n" +
                        "G. SHELL_EXEC (执行原生Linux Shell指令并返回结果，必填参数: param为shell指令)\n\n" +
                        "你必须【严格且仅能】以 JSON 格式回应，格式定义如下：\n" +
                        "{\n" +
                        "  \"thought\": \"你分析当前局势与决定下一步的思考过程\",\n" +
                        "  \"action\": \"从上述A/B/C/D/E/F/G中选择一个动作指令(如 READ_DEVICE)\",\n" +
                        "  \"action_param\": \"该动作需要的字符串参数(若无则留空)\",\n" +
                        "  \"is_finished\": false\n" +
                        "}\n" +
                        "当所有任务指标完成时，请将 action 设为 FINISH，is_finished 设为 true，并将最终汇总报告填入 action_param。\n" +
                        "绝对禁止输出 markdown 标记或其他无关文字，仅输出合法的 JSON！";

                    JSONObject sysMsg = new JSONObject();
                    sysMsg.put("role", "system");
                    sysMsg.put("content", prompt);
                    messages.put(sysMsg);
                    
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", "目标任务: " + task);
                    messages.put(userMsg);
                    
                    while (!finished && step < 12) {
                        step++;
                        final int currentStep = step;
                        
                        String responseData = syncDeepSeekCall(apiKey, selectedModel, messages);
                        String jsonStr = extractJsonFromText(responseData);
                        JSONObject agentRes = new JSONObject(jsonStr);
                        
                        final String thought = agentRes.optString("thought", "无推演...");
                        final String action = agentRes.optString("action", "TEXT_PROCESS");
                        final String param = agentRes.optString("action_param", "");
                        finished = agentRes.optBoolean("is_finished", false);
                        
                        mainHandler.post(new Runnable() {
                            public void run() {
                                String log = "\n[Step " + currentStep + "] 🧠 思考: " + thought + "\n⚡ 执行动作: " + action;
                                if (!param.isEmpty() && action.length() > 2) {
                                    log += "\n🔗 参数挂载: " + (param.length() > 50 ? param.substring(0, 50) + "..." : param);
                                }
                                aiMsg.finalContent += log + "\n───";
                                aiMsg.invalidateCache();
                                chatAdapter.notifyDataSetChanged();
                                lvChat.setSelection(chatList.size() - 1);
                            }
                        });
                        
                        if (finished) {
                            mainHandler.post(new Runnable() {
                                public void run() {
                                    aiMsg.finalContent += "\n\n✅ [工作流任务完结]\n" + param;
                                    aiMsg.invalidateCache();
                                    chatAdapter.notifyDataSetChanged();
                                }
                            });
                            break;
                        }
                        
                        String actionResult = executeAgentAction(action, param);
                        
                        JSONObject astMsg = new JSONObject();
                        astMsg.put("role", "assistant");
                        astMsg.put("content", jsonStr);
                        messages.put(astMsg);
                        
                        JSONObject resMsg = new JSONObject();
                        resMsg.put("role", "user");
                        resMsg.put("content", "系统底层执行结果回报:\n" + actionResult);
                        messages.put(resMsg);
                    }
                    
                    if (!finished) {
                        mainHandler.post(new Runnable() {
                            public void run() { aiMsg.finalContent += "\n\n❌ [安全阻断] 工作流循环次数超出上限，强制终止。"; aiMsg.invalidateCache(); chatAdapter.notifyDataSetChanged(); }
                        });
                    }
                    
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() { aiMsg.finalContent += "\n\n❌ [引擎崩溃] 解析失败或网络异常: " + e.getMessage(); aiMsg.invalidateCache(); chatAdapter.notifyDataSetChanged(); }
                    });
                } finally {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            isAiTyping = false;
                            tvStatus.setText("● 待命");
                            tvStatus.setTextColor(0xFF27AE60);
                            if (!aiMsg.finalContent.isEmpty()) {
                                db.addChatMessage(reqSessionId, "ai", aiMsg.toRawString());
                                refreshSessionList();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void callDeepSeekAPI(final String apiKey) {
        isAiTyping = true;
        tvStatus.setText("● 运算中...");
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
        sysPrompt.append("你是一个运行在移动端极客应用「小欲」中的终端神经网络。请保持专业、精简的黑客风格回答。\n\n");
        sysPrompt.append("【当前宿主设备物理状态】\n");
        sysPrompt.append("- 本地系统精确时间: ").append(currentTime).append("\n");
        if (batteryPct != -1) sysPrompt.append("- 物理电池剩余电量: ").append(batteryPct).append("%\n");
        if (totalRam > 0) sysPrompt.append("- 内存(RAM)状态: 剩余 ").append(availRam).append("MB / 总共 ").append(totalRam).append("MB\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Response response = null;
                BufferedReader reader = null;
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("model", selectedModel); 
                    payload.put("stream", true); 
                    
                    if (isSearchMode) {
                        payload.put("search", true); 
                        payload.put("net", true);
                    }
                    
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
                    int startIndex = Math.max(0, history.size() - 20); 
                    for (int i = startIndex; i < history.size(); i++) {
                        String[] h = history.get(i);
                        JSONObject m = new JSONObject();
                        m.put("role", h[0].equals("ai") ? "assistant" : "user");
                        m.put("content", h[1]);
                        messages.put(m);
                    }
                    payload.put("messages", messages);

                    RequestBody body = RequestBody.create(JSON_MEDIA, payload.toString());
                    Request request = new Request.Builder()
                            .url("https://api.deepseek.com/chat/completions")
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .addHeader("Accept", "text/event-stream")
                            .post(body)
                            .build();

                    response = okHttpClient.newCall(request).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        InputStream is = response.body().byteStream();
                        reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
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
                                        
                                        if (delta != null) {
                                            final StringBuilder chunkThink = new StringBuilder();
                                            final StringBuilder chunkFinal = new StringBuilder();

                                            if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                                String rc = delta.optString("reasoning_content", "");
                                                if (!rc.isEmpty() && !rc.equals("null")) {
                                                    chunkThink.append(rc);
                                                }
                                            }
                                            
                                            if (delta.has("content") && !delta.isNull("content")) {
                                                String cStr = delta.optString("content", "");
                                                if (!cStr.isEmpty() && !cStr.equals("null")) {
                                                    chunkFinal.append(cStr);
                                                }
                                            }
                                            
                                            if (chunkThink.length() > 0 || chunkFinal.length() > 0) {
                                                final String newThink = chunkThink.toString();
                                                final String newFinal = chunkFinal.toString();
                                                mainHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        aiMsg.thinkContent += newThink;
                                                        aiMsg.finalContent += newFinal;
                                                        aiMsg.invalidateCache();
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
                        final String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        final int code = response.code();
                        mainHandler.post(new Runnable() {
                            @Override public void run() { aiMsg.finalContent += "\n[API 拒绝连线: 状态码 " + code + "]\n" + errorBody; aiMsg.invalidateCache(); }
                        });
                    }
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override public void run() { aiMsg.finalContent += "\n[OkHttp引擎崩溃: " + e.getMessage() + "]"; aiMsg.invalidateCache(); }
                    });
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception e) {}
                    if (response != null) response.close();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isAiTyping = false;
                            tvStatus.setText("● 待命");
                            tvStatus.setTextColor(0xFF27AE60);
                            chatAdapter.notifyDataSetChanged();
                            if (!aiMsg.thinkContent.isEmpty() || !aiMsg.finalContent.isEmpty()) {
                                db.addChatMessage(reqSessionId, "ai", aiMsg.toRawString());
                                refreshSessionList();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private String syncDeepSeekCall(String apiKey, String model, JSONArray messages) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("stream", false); 
        
        JSONObject format = new JSONObject();
        format.put("type", "json_object");
        payload.put("response_format", format); 
        payload.put("messages", messages);
        
        RequestBody body = RequestBody.create(JSON_MEDIA, payload.toString());
        Request request = new Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
                
        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String resStr = response.body().string();
            JSONObject res = new JSONObject(resStr);
            return res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        } else {
            throw new Exception("OkHttp 拒绝连线: 状态码 " + response.code());
        }
    }

    private String extractJsonFromText(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start != -1 && end != -1 && end >= start) {
            return raw.substring(start, end + 1);
        }
        return "{}";
    }

    private String executeAgentAction(String action, final String param) {
        if ("READ_DEVICE".equals(action)) {
            long availRam = 0, totalRam = 0;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                if (am != null) { am.getMemoryInfo(mi); availRam = mi.availMem / 1048576L; totalRam = mi.totalMem / 1048576L;}
            } catch (Exception e) {}
            
            int batteryPct = -1;
            try {
                Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null) {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPct = (int) (level * 100 / (float) scale);
                }
            } catch (Exception e) {}
            
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            return "设备时间: " + time + "\n剩余电量: " + batteryPct + "%\nRAM: 剩余 " + availRam + "MB / 总计 " + totalRam + "MB";
            
        } else if ("ENCRYPT_SAVE".equals(action)) {
            try {
                if (param == null || param.isEmpty()) return "操作失败：缺少需要加密的文本。";
                File baseDir = new File(Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault/Agent");
                if (!baseDir.exists()) baseDir.mkdirs();
                
                String fileName = "Agent_Report_" + System.currentTimeMillis() + ".snt";
                File outFile = new File(baseDir, fileName);
                
                String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                if (deviceId == null) deviceId = "xiaoyu_fallback_id";
                
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] key = digest.digest((deviceId + "_xiaoyu_agent_master_key").getBytes("UTF-8"));
                SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] iv = new byte[16];
                new SecureRandom().nextBytes(iv);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

                FileOutputStream fos = new FileOutputStream(outFile);
                fos.write(iv);
                CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                cos.write(param.getBytes("UTF-8"));
                cos.flush();
                cos.close();
                
                return "加密成功。文件已物理固化至目录: " + outFile.getAbsolutePath();
            } catch (Exception e) {
                return "加密失败: " + e.getMessage();
            }
        } else if ("SCRAPE_WEB".equals(action)) {
            try {
                Document doc = Jsoup.connect(param).timeout(15000).get();
                return "抓取成功。文本前500字:\n" + doc.text().substring(0, Math.min(doc.text().length(), 500));
            } catch (Exception e) {
                return "抓取失败: " + e.getMessage();
            }
        } else if ("FILE_READ".equals(action)) {
            try {
                File f = new File(param);
                if (!f.exists()) return "读取失败: 文件不存在";
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 100) { 
                    sb.append(line).append("\n");
                    count++;
                }
                br.close();
                return "读取成功。前100行内容:\n" + sb.toString();
            } catch (Exception e) {
                return "读取失败: " + e.getMessage();
            }
        } else if ("CLIPBOARD_MANAGE".equals(action)) {
            try {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("AgentData", param));
                return "已静默写入系统剪贴板。";
            } catch (Exception e) {
                return "写入剪贴板失败: " + e.getMessage();
            }
        } else if ("SHELL_EXEC".equals(action)) {
            try {
                Process process = Runtime.getRuntime().exec(param);
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                return "Shell执行结果:\n" + sb.toString();
            } catch (Exception e) {
                return "Shell执行失败: " + e.getMessage();
            }
        } else if ("TEXT_PROCESS".equals(action)) {
            return "本地文本处理就绪。由于您处于Agent引擎内，该操作实质为空轮转，请直接输出最终分析结果。";
        }
        return "未知错误：无效的 action 指令。";
    }

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
            .setTitle("⚙️ 部署 API 密钥")
            .setMessage("请输入您的 DeepSeek API Key：")
            .setView(container)
            .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sp.edit().putString("deepseek_api_key", input.getText().toString().trim()).apply();
                    Toast.makeText(AiChatActivity.this, "密钥已装载", Toast.LENGTH_SHORT).show();
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
            .setTitle("📝 修改链路名称")
            .setView(container)
            .setPositiveButton("保存", new DialogInterface.OnClickListener() {
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
            .setTitle("销毁链路")
            .setMessage("确定要永久删除此对话吗？")
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    db.deleteSession(targetId);
                    refreshSessionList();
                    if (targetId == currentSessionId) createNewSession();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void initSession() {
        sessionList.addAll(db.getAllSessions());
        if (sessionList.isEmpty()) {
            createNewSession();
        } else {
            String[] lastSession = sessionList.get(0);
            currentSessionId = Long.parseLong(lastSession[0]);
            currentSessionTitle = lastSession[1];
            loadChatHistoryForCurrentSession();
        }
        sessionAdapter.notifyDataSetChanged();
    }

    private void createNewSession() {
        String timeStr = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date());
        currentSessionTitle = "新神经链路 " + timeStr;
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
        ChatMessage msg = new ChatMessage(role, content);
        chatList.add(msg);
        if (saveToDb) {
            db.addChatMessage(currentSessionId, role, msg.toRawString());
            refreshSessionList(); 
        }
        chatAdapter.notifyDataSetChanged();
        lvChat.setSelection(chatList.size() - 1);
    }

    private void openDrawer() { refreshSessionList(); layoutDrawer.setVisibility(View.VISIBLE); }
    private void closeDrawer() { layoutDrawer.setVisibility(View.GONE); }

    private class ChatMessage {
        String role;
        String thinkContent = "";
        String finalContent = "";
        boolean isThinkVisible = false;
        
        SpannableStringBuilder cachedSpan; 

        public ChatMessage(String role, String rawContent) { 
            this.role = role;
            parseContent(rawContent);
        }

        public void invalidateCache() {
            this.cachedSpan = null;
        }

        private void parseContent(String rawContent) {
            if (rawContent == null || rawContent.isEmpty()) {
                this.finalContent = "";
                return;
            }
            String thinkPrefix = "💡 深度推演中...\n";
            String separator = "\n\n───\n";
            
            if (rawContent.contains(thinkPrefix)) {
                int start = rawContent.indexOf(thinkPrefix) + thinkPrefix.length();
                int end = rawContent.indexOf(separator);
                if (end != -1) {
                    this.thinkContent = rawContent.substring(start, end);
                    this.finalContent = rawContent.substring(end + separator.length());
                } else {
                    this.thinkContent = rawContent.substring(start);
                    this.finalContent = "";
                }
            } else {
                this.thinkContent = "";
                this.finalContent = rawContent;
            }
        }

        public String toRawString() {
            StringBuilder sb = new StringBuilder();
            if (!thinkContent.isEmpty()) {
                sb.append("💡 深度推演中...\n").append(thinkContent);
                if (!finalContent.isEmpty()) {
                    sb.append("\n\n───\n");
                }
            }
            sb.append(finalContent);
            return sb.toString();
        }
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

            tvAi.setMovementMethod(LinkMovementMethod.getInstance());
            tvAi.setHighlightColor(android.graphics.Color.TRANSPARENT);

            tvUser.setMovementMethod(LinkMovementMethod.getInstance());
            tvUser.setHighlightColor(android.graphics.Color.TRANSPARENT);

            final int pos = position;
            final ChatMessage msg = chatList.get(pos);

            if (msg.role.equals("ai")) {
                layoutAi.setVisibility(View.VISIBLE); 
                layoutUser.setVisibility(View.GONE); 

                if (msg.cachedSpan == null) {
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (!msg.thinkContent.isEmpty()) {
                        String toggleText = msg.isThinkVisible ? "▼ 收起思考过程\n\n" : "▶ 展开思考过程\n\n";
                        if (msg.finalContent.isEmpty() && !msg.isThinkVisible) {
                            toggleText = "▶ 展开思考过程 (推演中...)\n\n";
                        }
                        
                        int startToggle = ssb.length();
                        ssb.append(toggleText);
                        int endToggle = ssb.length();
                        
                        ClickableSpan clickSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View widget) {
                                chatList.get(pos).isThinkVisible = !chatList.get(pos).isThinkVisible;
                                chatList.get(pos).invalidateCache();
                                notifyDataSetChanged();
                            }
                            @Override
                            public void updateDrawState(TextPaint ds) {
                                super.updateDrawState(ds);
                                ds.setColor(0xFF8E44AD); 
                                ds.setUnderlineText(false);
                                ds.setFakeBoldText(true);
                            }
                        };
                        ssb.setSpan(clickSpan, startToggle, endToggle, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        
                        if (msg.isThinkVisible) {
                            int startThink = ssb.length();
                            ssb.append(msg.thinkContent);
                            ssb.append("\n\n───\n\n");
                            int endThink = ssb.length();
                            
                            ssb.setSpan(new ForegroundColorSpan(0xFF888888), startThink, endThink, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.setSpan(new RelativeSizeSpan(0.85f), startThink, endThink, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    
                    ssb.append(msg.finalContent);

                    int startCopy = ssb.length();
                    ssb.append("\n\n[ 📋 复制内容 ]");
                    int endCopy = ssb.length();
                    
                    ClickableSpan copySpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(ClipData.newPlainText("AiChat", chatList.get(pos).toRawString()));
                            Toast.makeText(AiChatActivity.this, "内容已安全复制", Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void updateDrawState(TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(0xFF4A90E2); 
                            ds.setUnderlineText(false);
                            ds.setFakeBoldText(true);
                        }
                    };
                    ssb.setSpan(copySpan, startCopy, endCopy, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    msg.cachedSpan = ssb;
                }
                tvAi.setText(msg.cachedSpan);

            } else {
                layoutAi.setVisibility(View.GONE); 
                layoutUser.setVisibility(View.VISIBLE); 
                
                if (msg.cachedSpan == null) {
                    SpannableStringBuilder userSsb = new SpannableStringBuilder();
                    userSsb.append(msg.finalContent);

                    int startCopyUser = userSsb.length();
                    userSsb.append("\n\n[ 📋 复制内容 ]");
                    int endCopyUser = userSsb.length();
                    
                    ClickableSpan copyUserSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(ClipData.newPlainText("UserChat", chatList.get(pos).finalContent));
                            Toast.makeText(AiChatActivity.this, "内容已安全复制", Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void updateDrawState(TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(0xFF4A90E2); 
                            ds.setUnderlineText(false);
                            ds.setFakeBoldText(true);
                        }
                    };
                    userSsb.setSpan(copyUserSpan, startCopyUser, endCopyUser, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    msg.cachedSpan = userSsb;
                }
                tvUser.setText(msg.cachedSpan);
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
