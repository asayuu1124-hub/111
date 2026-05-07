package com.asayuu.com;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoToolActivity extends Activity implements View.OnClickListener {

    private EditText etInput, etKey;
    private TextView tvResult;

    // --- 摩斯密碼字典矩陣 ---
    private static final String[] MORSE_CHARS = {
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", 
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", 
        ".", ",", "?", "!", "'", "\"", "(", ")", "&", ":", ";", "=", "+", "-", "_", "$", "@"
    };
    private static final String[] MORSE_CODES = {
        ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", 
        "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..", 
        "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----.", 
        ".-.-.-", "--..--", "..--..", "-.-.--", ".----.", ".-..-.", "-.--.", "-.--.-", ".-...", "---...", "-.-.-.", "-...-", ".-.-.", "-....-", "..--.-", "...-..-", ".--.-."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crypto_tool);

        etInput = (EditText) findViewById(R.id.et_crypto_input);
        etKey = (EditText) findViewById(R.id.et_crypto_key);
        tvResult = (TextView) findViewById(R.id.tv_crypto_result);

        findViewById(R.id.btn_md5).setOnClickListener(this);
        findViewById(R.id.btn_sha256).setOnClickListener(this);
        findViewById(R.id.btn_b64_enc).setOnClickListener(this);
        findViewById(R.id.btn_b64_dec).setOnClickListener(this);
        findViewById(R.id.btn_url_enc).setOnClickListener(this);
        findViewById(R.id.btn_url_dec).setOnClickListener(this);
        
        // 綁定摩斯密碼按鈕
        findViewById(R.id.btn_morse_enc).setOnClickListener(this);
        findViewById(R.id.btn_morse_dec).setOnClickListener(this);

        findViewById(R.id.btn_aes_enc).setOnClickListener(this);
        findViewById(R.id.btn_aes_dec).setOnClickListener(this);
        findViewById(R.id.btn_des_enc).setOnClickListener(this);
        findViewById(R.id.btn_des_dec).setOnClickListener(this);
        
        findViewById(R.id.btn_copy_result).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String res = tvResult.getText().toString();
                if (!res.isEmpty() && !res.contains("處理結果將顯示在此處")) {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("CryptoResult", res));
                    Toast.makeText(CryptoToolActivity.this, "已複製到剪貼簿", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        String input = etInput.getText().toString().trim();
        String key = etKey.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, "請先輸入文本", Toast.LENGTH_SHORT).show();
            return;
        }

        String result = "";
        int id = v.getId();
        
        try {
            if (id == R.id.btn_md5) result = getHash(input, "MD5");
            else if (id == R.id.btn_sha256) result = getHash(input, "SHA-256");
            else if (id == R.id.btn_b64_enc) result = Base64.encodeToString(input.getBytes("UTF-8"), Base64.NO_WRAP);
            else if (id == R.id.btn_b64_dec) result = new String(Base64.decode(input, Base64.NO_WRAP), "UTF-8");
            else if (id == R.id.btn_url_enc) result = URLEncoder.encode(input, "UTF-8");
            else if (id == R.id.btn_url_dec) result = URLDecoder.decode(input, "UTF-8");
            else if (id == R.id.btn_morse_enc) result = encodeMorse(input);
            else if (id == R.id.btn_morse_dec) result = decodeMorse(input);
            else if (id == R.id.btn_aes_enc) result = doSymmetricCrypto(input, key, "AES", true);
            else if (id == R.id.btn_aes_dec) result = doSymmetricCrypto(input, key, "AES", false);
            else if (id == R.id.btn_des_enc) result = doSymmetricCrypto(input, key, "DES", true);
            else if (id == R.id.btn_des_dec) result = doSymmetricCrypto(input, key, "DES", false);
            
            tvResult.setText(result);
        } catch (Exception e) {
            tvResult.setText("❌ 處理失敗: \n" + e.getMessage());
        }
    }

    // --- 核心算法庫 ---

    private String encodeMorse(String input) {
        input = input.toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                sb.append("/ "); // 原文的空格用斜槓代表
                continue;
            }
            boolean found = false;
            for (int j = 0; j < MORSE_CHARS.length; j++) {
                if (MORSE_CHARS[j].charAt(0) == c) {
                    sb.append(MORSE_CODES[j]).append(" ");
                    found = true;
                    break;
                }
            }
            if (!found) {
                sb.append(c).append(" "); // 找不到的字元（如中文）原樣輸出
            }
        }
        return sb.toString().trim();
    }

    private String decodeMorse(String input) {
        StringBuilder sb = new StringBuilder();
        String[] words = input.split("/");
        for (String word : words) {
            String[] letters = word.trim().split("\\s+");
            for (String letter : letters) {
                boolean found = false;
                for (int j = 0; j < MORSE_CODES.length; j++) {
                    if (MORSE_CODES[j].equals(letter)) {
                        sb.append(MORSE_CHARS[j]);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sb.append(letter); // 找不到的符號原樣保留
                }
            }
            sb.append(" "); // 單詞解碼完畢補上空格
        }
        return sb.toString().trim();
    }

    private String getHash(String input, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] hash = md.digest(input.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private String doSymmetricCrypto(String input, String keyStr, String type, boolean isEncrypt) throws Exception {
        if (keyStr.isEmpty()) throw new Exception("請輸入 " + type + " 加密密鑰");

        int keyLen = type.equals("AES") ? 16 : 8;
        int ivLen = type.equals("AES") ? 16 : 8;
        String transform = type + "/CBC/PKCS5Padding";

        // 密鑰處理：無論用戶輸入什麼，一律哈希後截取指定長度，防止 InvalidKeyException
        byte[] keyBytes = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(keyStr.getBytes("UTF-8")), keyLen);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, type);

        Cipher cipher = Cipher.getInstance(transform);

        if (isEncrypt) {
            byte[] iv = new byte[ivLen];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            
            byte[] encrypted = cipher.doFinal(input.getBytes("UTF-8"));
            
            // 將 IV 藏入密文頭部
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } else {
            byte[] combined = Base64.decode(input, Base64.NO_WRAP);
            if (combined.length < ivLen) throw new Exception("無效的密文數據");
            
            // 提取頭部的 IV
            byte[] iv = new byte[ivLen];
            System.arraycopy(combined, 0, iv, 0, ivLen);
            byte[] encrypted = new byte[combined.length - ivLen];
            System.arraycopy(combined, ivLen, encrypted, 0, encrypted.length);
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), "UTF-8");
        }
    }
}