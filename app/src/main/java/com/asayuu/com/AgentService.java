package com.asayuu.com;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AgentService extends Service {
    private static final String CHANNEL_ID = "xiaoyu_agent_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Agent 守护状态机", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("用于维持 Agent 工作流的物理存活");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        Notification notification = builder.setContentTitle("小欲 Agent 守护中")
                .setContentText("后台状态机正在执行静默任务...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        
        startForeground(1001, notification);
        executeSilentAgentTask();
        
        return START_NOT_STICKY;
    }

    private void executeSilentAgentTask() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    String content = "【Agent 自动化静默巡检报告】\n唤醒时间: " + time + "\n物理链路状态: 正常\n\n(此文件由 AlarmManager 精准唤醒并经由 AgentService 后台执行流式 AES 落盘)";

                    File dir = new File(Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault/AgentBackground");
                    if (!dir.exists()) dir.mkdirs();

                    File outFile = new File(dir, "Silent_Report_" + System.currentTimeMillis() + ".snt");

                    String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
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
                    cos.write(content.getBytes("UTF-8"));
                    cos.flush();
                    cos.close();

                } catch (Exception e) {
                    // 静默捕获异常，阻断向系统抛出崩溃
                } finally {
                    stopForeground(true);
                    stopSelf();
                }
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}