package com.asayuu.com;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    // 物理注入的隐写控制矩阵
    private Button btnZwEnc, btnZwDec;

    // --- 摩斯密码字典矩阵 ---
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
        
        // 绑定摩斯密码按钮
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
                if (!res.isEmpty() && !res.contains("处理结果将显示在此处")) {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("CryptoResult", res));
                    Toast.makeText(CryptoToolActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            }
        });

        injectZeroWidthMatrix();
    }

    // --- 动态视图物理注入 (修复层叠溢出版) ---
    private void injectZeroWidthMatrix() {
        try {
            // 抓取相对布局容器
            ViewGroup relativeLayout = (ViewGroup) tvResult.getParent();
            // 向上追溯主线性容器，避免覆盖
            ViewGroup mainLinearLayout = (ViewGroup) relativeLayout.getParent();
            float density = getResources().getDisplayMetrics().density;
            
            LinearLayout llZero = new LinearLayout(this);
            llZero.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int)(10 * density);
            llZero.setLayoutParams(lp);

            btnZwEnc = new Button(this);
            btnZwEnc.setText("零宽隐写注入 (密文放密钥框)");
            btnZwEnc.setBackgroundResource(R.drawable.selector_neumorph_btn);
            btnZwEnc.setTextColor(Color.parseColor("#8E44AD"));
            btnZwEnc.setTextSize(11f);
            LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(0, (int)(45 * density), 1.0f);
            btnLp1.rightMargin = (int)(5 * density);
            btnZwEnc.setLayoutParams(btnLp1);
            btnZwEnc.setOnClickListener(this);

            btnZwDec = new Button(this);
            btnZwDec.setText("零宽隐写提取 (解析)");
            btnZwDec.setBackgroundResource(R.drawable.selector_neumorph_btn);
            btnZwDec.setTextColor(Color.parseColor("#8E44AD"));
            btnZwDec.setTextSize(11f);
            LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(0, (int)(45 * density), 1.0f);
            btnLp2.leftMargin = (int)(5 * density);
            btnZwDec.setLayoutParams(btnLp2);
            btnZwDec.setOnClickListener(this);

            llZero.addView(btnZwEnc);
            llZero.addView(btnZwDec);

            // 物理排版：将其注入到相对布局的正上方
            int insertIndex = mainLinearLayout.indexOfChild(relativeLayout);
            mainLinearLayout.addView(llZero, insertIndex > 0 ? insertIndex : mainLinearLayout.getChildCount() - 1);
        } catch (Exception e) {}
    }

    @Override
    public void onClick(View v) {
        String input = etInput.getText().toString().trim();
        String key = etKey.getText().toString().trim();

        // 仅在非隐写注入时强制校验公开载体输入
        if (input.isEmpty() && v != btnZwEnc) {
            Toast.makeText(this, "请先在【输入】框提供公开文本载体", Toast.LENGTH_SHORT).show();
            return;
        }

        String result = "";
        int id = v.getId();
        
        try {
            if (v == btnZwEnc) {
                if (key.isEmpty()) throw new Exception("拒绝操作：必须在【密钥】框提供需要隐藏的机密流");
                result = encodeZeroWidth(input, key);
            } else if (v == btnZwDec) {
                result = decodeZeroWidth(input);
            } else if (id == R.id.btn_md5) result = getHash(input, "MD5");
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
            tvResult.setText("❌ 装甲异常: \n" + e.getMessage());
        }
    }

    // --- 零宽字符隐写引擎底层算法 ---

    private String encodeZeroWidth(String carrier, String secret) throws Exception {
        if (carrier.isEmpty()) carrier = "未命名安全载体"; 

        byte[] bytes = secret.getBytes("UTF-8");
        StringBuilder hidden = new StringBuilder();
        hidden.append('\u200B'); // 零宽空格，界定数据块起点
        
        for (byte b : bytes) {
            for (int i = 7; i >= 0; i--) {
                int bit = (b >> i) & 1;
                // 强制二进制降维：1映射为零宽不连字，0映射为零宽连字
                hidden.append(bit == 1 ? '\u200C' : '\u200D'); 
            }
        }
        hidden.append('\u200B'); // 零宽空格，界定数据块终点

        // 物理缝隙注入：强制切开公共载体，将隐写流植入首字符间隙
        return carrier.substring(0, 1) + hidden.toString() + carrier.substring(1);
    }

    private String decodeZeroWidth(String mixed) throws Exception {
        StringBuilder binary = new StringBuilder();
        boolean isRecording = false;
        
        for (int i = 0; i < mixed.length(); i++) {
            char c = mixed.charAt(i);
            if (c == '\u200B') {
                isRecording = !isRecording; // 触碰界定符，切换拦截状态
                continue;
            }
            if (isRecording) {
                if (c == '\u200C') binary.append('1');
                else if (c == '\u200D') binary.append('0');
            }
        }

        if (binary.length() == 0 || binary.length() % 8 != 0) {
            throw new Exception("该文本未携带零宽装甲，或数据流已遭受结构性损毁。");
        }

        byte[] bytes = new byte[binary.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            String byteStr = binary.substring(i * 8, (i + 1) * 8);
            bytes[i] = (byte) Integer.parseInt(byteStr, 2);
        }
        return new String(bytes, "UTF-8");
    }

    // --- 历史密码学算法库 ---

    private String encodeMorse(String input) {
        input = input.toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                sb.append("/ "); // 原文的空格用斜杠代表
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
                sb.append(c).append(" "); // 找不到的字符（如中文）原样输出
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
                    sb.append(letter); // 找不到的符号原样保留
                }
            }
            sb.append(" "); // 单词解码完毕补上空格
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
        if (keyStr.isEmpty()) throw new Exception("请输入 " + type + " 加密密钥");

        int keyLen = type.equals("AES") ? 16 : 8;
        int ivLen = type.equals("AES") ? 16 : 8;
        String transform = type + "/CBC/PKCS5Padding";

        // 密钥处理：无论用户输入什么，一律哈希后截取指定长度，防止 InvalidKeyException
        byte[] keyBytes = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(keyStr.getBytes("UTF-8")), keyLen);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, type);

        Cipher cipher = Cipher.getInstance(transform);

        if (isEncrypt) {
            byte[] iv = new byte[ivLen];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            
            byte[] encrypted = cipher.doFinal(input.getBytes("UTF-8"));
            
            // 将 IV 藏入密文头部
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } else {
            byte[] combined = Base64.decode(input, Base64.NO_WRAP);
            if (combined.length < ivLen) throw new Exception("无效的密文数据");
            
            // 提取头部的 IV
            byte[] iv = new byte[ivLen];
            System.arraycopy(combined, 0, iv, 0, ivLen);
            byte[] encrypted = new byte[combined.length - ivLen];
            System.arraycopy(combined, ivLen, encrypted, 0, encrypted.length);
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), "UTF-8");
        }
    }
}
