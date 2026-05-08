package com.asayuu.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SafeBoxActivity extends Activity {

    private LinearLayout llSetup, llLogin, llVault;
    private EditText etRealPwd, etFakePwd, etLoginPwd;
    private ListView lvVaultFiles;
    
    private SharedPreferences sp;
    private String currentPassword = "";
    private File currentVaultDir;
    
    private List<File> currentFiles = new ArrayList<File>();
    private ArrayAdapter<String> adapter;
    private List<String> fileNames = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_box);
        
        sp = getSharedPreferences("safebox_config", MODE_PRIVATE);
        
        llSetup = (LinearLayout) findViewById(R.id.ll_setup);
        llLogin = (LinearLayout) findViewById(R.id.ll_login);
        llVault = (LinearLayout) findViewById(R.id.ll_vault);
        
        etRealPwd = (EditText) findViewById(R.id.et_real_pwd);
        etFakePwd = (EditText) findViewById(R.id.et_fake_pwd);
        etLoginPwd = (EditText) findViewById(R.id.et_login_pwd);
        lvVaultFiles = (ListView) findViewById(R.id.lv_vault_files);

        findViewById(R.id.btn_save_setup).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSetup(); }
        });
        
        findViewById(R.id.btn_login_vault).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loginVault(); }
        });
        
        findViewById(R.id.btn_add_note).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddNoteDialog(); }
        });
        
        findViewById(R.id.btn_import_file).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 1001);
            }
        });

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, fileNames);
        lvVaultFiles.setAdapter(adapter);
        
        lvVaultFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleFileClick(currentFiles.get(position));
            }
        });

        lvVaultFiles.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                new AlertDialog.Builder(SafeBoxActivity.this)
                    .setTitle("删除确认")
                    .setMessage("确定要永久销毁此文件吗？")
                    .setPositiveButton("销毁", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            currentFiles.get(position).delete();
                            loadFiles();
                        }
                    }).setNegativeButton("取消", null).show();
                return true;
            }
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            boolean hasPermission = false;
            try {
                // 物理级 I/O 探针测试，彻底取代高版本 API 反射，绕过编译器检查
                File testFile = new File(Environment.getExternalStorageDirectory(), ".safebox_test");
                if (testFile.exists()) testFile.delete();
                hasPermission = testFile.createNewFile();
                if (hasPermission) testFile.delete();
            } catch (Exception e) {
                hasPermission = false; 
            }
            
            if (!hasPermission) {
                try {
                    Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 1002);
                } catch (Exception e) {
                    startActivityForResult(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"), 1002);
                }
                return;
            }
        } else {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"}, 101);
                return;
            }
        }
        initUIState();
    }

    private void initUIState() {
        String realHash = sp.getString("real_hash", "");
        if (realHash.isEmpty()) {
            llSetup.setVisibility(View.VISIBLE);
        } else {
            llLogin.setVisibility(View.VISIBLE);
        }
    }

    private void saveSetup() {
        String real = etRealPwd.getText().toString();
        String fake = etFakePwd.getText().toString();
        if (real.isEmpty() || fake.isEmpty() || real.equals(fake)) {
            Toast.makeText(this, "密码不可为空且不能相同", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            sp.edit().putString("real_hash", getSHA256(real))
                     .putString("fake_hash", getSHA256(fake)).apply();
            llSetup.setVisibility(View.GONE);
            llLogin.setVisibility(View.VISIBLE);
            Toast.makeText(this, "固化成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {}
    }

    private void loginVault() {
        String pwd = etLoginPwd.getText().toString();
        try {
            String hash = getSHA256(pwd);
            if (hash.equals(sp.getString("real_hash", ""))) {
                mountVault(pwd, "Real");
            } else if (hash.equals(sp.getString("fake_hash", ""))) {
                mountVault(pwd, "Fake");
            } else {
                Toast.makeText(this, "密钥错误", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {}
    }

    private void mountVault(String pwd, String folderType) {
        currentPassword = pwd;
        File baseDir = new File(Environment.getExternalStorageDirectory(), "Download/.XiaoyuVault");
        currentVaultDir = new File(baseDir, folderType);
        if (!currentVaultDir.exists()) currentVaultDir.mkdirs();
        
        llLogin.setVisibility(View.GONE);
        llVault.setVisibility(View.VISIBLE);
        loadFiles();
    }

    private void loadFiles() {
        fileNames.clear();
        currentFiles.clear();
        if (currentVaultDir != null && currentVaultDir.exists()) {
            File[] files = currentVaultDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    currentFiles.add(f);
                    fileNames.add(f.getName().endsWith(".snt") ? "📝 " + f.getName().replace(".snt", "") : "📁 " + f.getName());
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddNoteDialog() {
        final Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(R.drawable.nm_card_bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📝 新建加密记事");
        tvTitle.setTextColor(0xFF333333);
        tvTitle.setTextSize(16);
        layout.addView(tvTitle);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("标题...");
        etTitle.setBackgroundResource(R.drawable.nm_input_inset);
        etTitle.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(-1, -2);
        lp1.topMargin = 30;
        layout.addView(etTitle, lp1);

        final EditText etContent = new EditText(this);
        etContent.setHint("内容...");
        etContent.setBackgroundResource(R.drawable.nm_input_inset);
        etContent.setPadding(30, 30, 30, 30);
        etContent.setGravity(Gravity.TOP);
        etContent.setMinLines(6);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.topMargin = 30;
        layout.addView(etContent, lp2);

        Button btnSave = new Button(this);
        btnSave.setText("加 密 保 存");
        btnSave.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnSave.setTextColor(0xFF4A90E2);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(-1, 150);
        lp3.topMargin = 40;
        layout.addView(btnSave, lp3);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String title = etTitle.getText().toString();
                String content = etContent.getText().toString();
                if (!title.isEmpty() && !content.isEmpty()) {
                    try {
                        File outFile = new File(currentVaultDir, title + ".snt");
                        encryptBytesToFile(content.getBytes("UTF-8"), outFile, currentPassword);
                        loadFiles();
                        dialog.dismiss();
                    } catch (Exception e) {
                        Toast.makeText(SafeBoxActivity.this, "加密失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        dialog.show();
        resizeDialog(dialog);
    }

    private void handleFileClick(final File file) {
        if (file.getName().endsWith(".snt")) {
            try {
                byte[] data = decryptFileToBytes(file, currentPassword);
                showNoteContentDialog(file.getName().replace(".snt", ""), new String(data, "UTF-8"));
            } catch (Exception e) {
                Toast.makeText(this, "解密失败或密钥不匹配", Toast.LENGTH_SHORT).show();
            }
        } else {
            new AlertDialog.Builder(this)
                .setTitle("导出文件")
                .setMessage("是否将此文件解密并导出至 Download/XiaoyuExport 目录？")
                .setPositiveButton("导出", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            File exportDir = new File(Environment.getExternalStorageDirectory(), "Download/XiaoyuExport");
                            if (!exportDir.exists()) exportDir.mkdirs();
                            File exportedFile = new File(exportDir, file.getName());
                            decryptFileToFile(file, exportedFile, currentPassword);
                            Toast.makeText(SafeBoxActivity.this, "导出成功: " + exportedFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(SafeBoxActivity.this, "导出失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("取消", null).show();
        }
    }

    private void showNoteContentDialog(final String title, final String content) {
        final Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(R.drawable.nm_card_bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📝 " + title);
        tvTitle.setTextColor(0xFF333333);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 600);
        scrollLp.topMargin = 30;
        
        TextView tvContent = new TextView(this);
        tvContent.setText(content);
        tvContent.setTextColor(0xFF555555);
        tvContent.setTextSize(15);
        scroll.addView(tvContent);
        
        layout.addView(scroll, scrollLp);

        // 新增的水平按钮列：一键复制与阅毕
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnLayoutLp = new LinearLayout.LayoutParams(-1, 150);
        btnLayoutLp.topMargin = 40;

        Button btnCopy = new Button(this);
        btnCopy.setText("复 制");
        btnCopy.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnCopy.setTextColor(0xFF4A90E2);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        copyLp.rightMargin = 15;
        btnLayout.addView(btnCopy, copyLp);

        Button btnClose = new Button(this);
        btnClose.setText("阅 毕");
        btnClose.setBackgroundResource(R.drawable.selector_neumorph_btn);
        btnClose.setTextColor(0xFF888888);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        btnLayout.addView(btnClose, closeLp);

        layout.addView(btnLayout, btnLayoutLp);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // 剪贴板逻辑注入
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText(title, content));
                Toast.makeText(SafeBoxActivity.this, "内容已安全复制", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnClose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        
        dialog.show();
        resizeDialog(dialog);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002) {
            checkAndRequestPermissions();
        } else if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri != null) showImportNameDialog(uri);
        }
    }

    private void showImportNameDialog(final Uri uri) {
        final EditText input = new EditText(this);
        input.setHint("输入保存的文件名 (含后缀)");
        new AlertDialog.Builder(this)
            .setTitle("导入文件")
            .setView(input)
            .setPositiveButton("加密导入", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString();
                    if (!name.isEmpty()) {
                        try {
                            InputStream is = getContentResolver().openInputStream(uri);
                            File outFile = new File(currentVaultDir, name);
                            encryptStreamToFile(is, outFile, currentPassword);
                            loadFiles();
                            Toast.makeText(SafeBoxActivity.this, "导入成功", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(SafeBoxActivity.this, "导入失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }).setNegativeButton("取消", null).show();
    }

    // --- 底层加密引擎 ---
    
    private String getSHA256(String str) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(str.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private SecretKeySpec generateAESKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(password.getBytes("UTF-8"));
        return new SecretKeySpec(key, "AES");
    }

    private void encryptBytesToFile(byte[] data, File outFile, String password) throws Exception {
        SecretKeySpec key = generateAESKey(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(iv);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);
        cos.write(data);
        cos.flush();
        cos.close();
    }

    private void encryptStreamToFile(InputStream is, File outFile, String password) throws Exception {
        SecretKeySpec key = generateAESKey(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(iv);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);
        byte[] b = new byte[8192];
        int d;
        while ((d = is.read(b)) != -1) {
            cos.write(b, 0, d);
        }
        cos.flush();
        cos.close();
        is.close();
    }

    private byte[] decryptFileToBytes(File inFile, String password) throws Exception {
        FileInputStream fis = new FileInputStream(inFile);
        byte[] iv = new byte[16];
        fis.read(iv);
        SecretKeySpec key = generateAESKey(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        CipherInputStream cis = new CipherInputStream(fis, cipher);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int d;
        while ((d = cis.read(b)) != -1) {
            baos.write(b, 0, d);
        }
        cis.close();
        return baos.toByteArray();
    }

    private void decryptFileToFile(File inFile, File outFile, String password) throws Exception {
        FileInputStream fis = new FileInputStream(inFile);
        byte[] iv = new byte[16];
        fis.read(iv);
        SecretKeySpec key = generateAESKey(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        CipherInputStream cis = new CipherInputStream(fis, cipher);
        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] b = new byte[8192];
        int d;
        while ((d = cis.read(b)) != -1) {
            fos.write(b, 0, d);
        }
        fos.flush();
        fos.close();
        cis.close();
    }

    private void resizeDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(lp);
        }
    }
}
