package com.asayuu.com;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class RegisterActivity extends Activity {
    private EditText etUser, etPass, etConfirm;
    private Button btnReg;
    private UserTask userTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userTask = new UserTask();
        etUser = (EditText) findViewById(R.id.et_reg_user);
        etPass = (EditText) findViewById(R.id.et_reg_pass);
        etConfirm = (EditText) findViewById(R.id.et_reg_confirm);
        btnReg = (Button) findViewById(R.id.btn_do_reg);

        btnReg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String u = etUser.getText().toString().trim();
                final String p = etPass.getText().toString().trim();
                String cp = etConfirm.getText().toString().trim();

                if (u.isEmpty() || p.isEmpty() || !p.equals(cp)) {
                    Toast.makeText(RegisterActivity.this, "信息输入有误", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnReg.setEnabled(false);
                btnReg.setText("正在提交云端...");

                userTask.execute("register", u, p, new UserTask.UserCallback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        btnReg.setEnabled(true);
                        btnReg.setText("完 成 注 册");
                        if (success) {
                            Toast.makeText(RegisterActivity.this, "云端注册成功，请登录", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "注册失败: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }
}