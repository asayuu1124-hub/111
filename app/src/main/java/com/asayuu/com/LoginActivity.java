package com.asayuu.com;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler; // 必须导入
import android.view.View;
import android.widget.*;

public class LoginActivity extends Activity {
    private EditText etUser, etPass;
    private Button btnLogin;
    private TextView tvReg;
    private CheckBox cbRemember, cbAutoLogin;
    private RelativeLayout loadingOverlay;
    private TextView tvLoadingMsg;
    
    private UserTask userTask;
    private SharedPreferences sp;
    private Handler mainHandler = new Handler(); // 用于延迟处理

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. 初始化
        userTask = new UserTask();
        sp = getSharedPreferences("asayuu_config", MODE_PRIVATE);
        
        etUser = (EditText) findViewById(R.id.et_login_user);
        etPass = (EditText) findViewById(R.id.et_login_pass);
        btnLogin = (Button) findViewById(R.id.btn_do_login);
        tvReg = (TextView) findViewById(R.id.tv_go_register);
        cbRemember = (CheckBox) findViewById(R.id.cb_remember);
        cbAutoLogin = (CheckBox) findViewById(R.id.cb_auto_login);
        loadingOverlay = (RelativeLayout) findViewById(R.id.loading_overlay);
        tvLoadingMsg = (TextView) findViewById(R.id.tv_loading_msg);

        // 2. 核心：增强版自动登录自检
        checkPreferenceAndLogin();

        // 3. 登录按钮监听
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String u = etUser.getText().toString().trim();
                final String p = etPass.getText().toString().trim();

                if (u.isEmpty() || p.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "请输入完整信息", Toast.LENGTH_SHORT).show();
                    return;
                }

                showLoading("正在同步云端凭据...");

                userTask.execute("login", u, p, new UserTask.UserCallback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        hideLoading();
                        if (success) {
                            handleLoginSuccess(u, p);
                        } else {
                            // 如果自动登录失败，清除标记，防止下次依然尝试失败的请求
                            sp.edit().putBoolean("auto_login", false).apply();
                            cbAutoLogin.setChecked(false);
                            Toast.makeText(LoginActivity.this, "登录失效: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        tvReg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
    }

    private void showLoading(String msg) {
        tvLoadingMsg.setText(msg);
        loadingOverlay.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
        btnLogin.setEnabled(true);
    }

    /**
     * 增强版：带延迟的自动登录触发
     */
    private void checkPreferenceAndLogin() {
        // A. 基础回填：记住密码
        if (sp.getBoolean("remember_pass", false)) {
            etUser.setText(sp.getString("saved_user", ""));
            etPass.setText(sp.getString("saved_pass", ""));
            cbRemember.setChecked(true);
        }

        // B. 自动登录逻辑
        final boolean isAuto = sp.getBoolean("auto_login", false);
        if (isAuto) {
            cbAutoLogin.setChecked(true);
            // 使用 Handler 延迟 500ms 执行，确保 View 已经完全绘制且网络通道可用
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 只有当账号密码都不为空时才触发
                    String u = etUser.getText().toString().trim();
                    String p = etPass.getText().toString().trim();
                    if (!u.isEmpty() && !p.isEmpty()) {
                        btnLogin.performClick();
                    }
                }
            }, 500);
        }
    }

    private void handleLoginSuccess(String u, String p) {
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("current_user", u);
        
        // 只有勾选了记住密码才保存账号密码
        if (cbRemember.isChecked()) {
            editor.putBoolean("remember_pass", true);
            editor.putString("saved_user", u);
            editor.putString("saved_pass", p);
        } else {
            editor.remove("saved_pass"); // 没勾选就清理掉
        }
        
        // 写入自动登录标志
        editor.putBoolean("auto_login", cbAutoLogin.isChecked());
        editor.apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}