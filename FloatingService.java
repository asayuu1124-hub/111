package com.asayuu.com;

import android.app.Service;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View ballView, windowView;
    private WindowManager.LayoutParams params;
    private int mWidth = 600, mHeight = 450;
    
    private RelativeLayout rootContainer;
    private LinearLayout llTransCard;
    private EditText etInput;
    private TextView tvResult, tvToggle;
    private ImageView ivCopy;
    private SeekBar sbAlpha;

    private boolean isPassiveMode = true;
    private String lastClip = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        trustAllSSL();
        initParams();
        initBall();
        initWindow();
        windowManager.addView(ballView, params);
        initClipboardListener();
    }

    private void trustAllSSL() {
        try {
            TrustManager[] tm = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, tm, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String h, SSLSession s) { return true; }
            });
        } catch (Exception e) {}
    }

    private void initParams() {
        int type = (Build.VERSION.SDK_INT >= 26) ? 2038 : 2002;
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200; params.y = 200;
    }

    private void initBall() {
        ballView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        ballView.setOnTouchListener(new TouchListener(true));
    }

    private void initWindow() {
        windowView = LayoutInflater.from(this).inflate(R.layout.view_floating_window, null);
        rootContainer = (RelativeLayout) windowView.findViewById(R.id.root_container);
        llTransCard = (LinearLayout) windowView.findViewById(R.id.ll_trans_card);
        etInput = (EditText) windowView.findViewById(R.id.et_trans_input);
        tvResult = (TextView) windowView.findViewById(R.id.tv_trans_result);
        tvToggle = (TextView) windowView.findViewById(R.id.tv_mode_toggle);
        ivCopy = (ImageView) windowView.findViewById(R.id.iv_copy_trans);
        sbAlpha = (SeekBar) windowView.findViewById(R.id.sb_trans_alpha);

        tvToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isPassiveMode = !isPassiveMode;
                tvToggle.setText("被动模式: " + (isPassiveMode ? "ON" : "OFF"));
            }
        });

        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || 
                   (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String text = etInput.getText().toString().trim();
                    if (!text.isEmpty()) performTranslate(text);
                    return true;
                }
                return false;
            }
        });

        ivCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("NeoTrans", tvResult.getText()));
                Toast.makeText(FloatingService.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });

        sbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                if (rootContainer.getBackground()!=null) rootContainer.getBackground().mutate().setAlpha(p);
                if (llTransCard.getBackground()!=null) llTransCard.getBackground().mutate().setAlpha(p);
                if (etInput.getBackground()!=null) etInput.getBackground().mutate().setAlpha(p);
                if (tvResult.getBackground()!=null) tvResult.getBackground().mutate().setAlpha(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        etInput.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==0 && (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                    params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(windowView, params);
                }
                return false;
            }
        });

        windowView.findViewById(R.id.menu_translate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                llTransCard.setVisibility(llTransCard.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        windowView.findViewById(R.id.btn_resize).setOnTouchListener(new View.OnTouchListener() {
            private int sW, sH; private float sX, sY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) { sW=params.width; sH=params.height; sX=e.getRawX(); sY=e.getRawY(); return true; }
                if (e.getAction()==MotionEvent.ACTION_MOVE) {
                    params.width = Math.max(400, sW+(int)(e.getRawX()-sX));
                    params.height = Math.max(300, sH+(int)(e.getRawY()-sY));
                    mWidth=params.width; mHeight=params.height;
                    windowManager.updateViewLayout(windowView, params);
                }
                return true;
            }
        });

        windowView.findViewById(R.id.btn_minimize).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.removeView(windowView);
                params.width=WindowManager.LayoutParams.WRAP_CONTENT; params.height=WindowManager.LayoutParams.WRAP_CONTENT;
                windowManager.addView(ballView, params);
            }
        });

        windowView.findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSelf(); }
        });
        
        windowView.findViewById(R.id.btn_drag).setOnTouchListener(new TouchListener(false));
    }

    private class TouchListener implements View.OnTouchListener {
        private boolean isB; private int iX, iY; private float itX, itY;
        public TouchListener(boolean b) { isB=b; }
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_DOWN) { iX=params.x; iY=params.y; itX=e.getRawX(); itY=e.getRawY(); return true; }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                params.x=iX+(int)(e.getRawX()-itX); params.y=iY+(int)(e.getRawY()-itY);
                windowManager.updateViewLayout(isB?ballView:windowView, params);
                return true;
            }
            if (isB && e.getAction()==MotionEvent.ACTION_UP && Math.abs(e.getRawX()-itX)<10) {
                windowManager.removeView(ballView);
                params.width=mWidth; params.height=mHeight;
                windowManager.addView(windowView, params);
            }
            return true;
        }
    }

    private void initClipboardListener() {
        final ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.addPrimaryClipChangedListener(new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override public void onPrimaryClipChanged() {
                if (!isPassiveMode) return;
                ClipData d = cb.getPrimaryClip();
                if (d!=null && d.getItemCount()>0) {
                    CharSequence t = d.getItemAt(0).getText();
                    if (t!=null && !t.toString().equals(lastClip)) {
                        lastClip = t.toString();
                        performTranslate(lastClip);
                    }
                }
            }
        });
    }

    private void performTranslate(final String text) {
        tvResult.setText("正在翻译...");
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = doTranslate(text);
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (result != null) {
                            tvResult.setText(result);
                            ivCopy.setVisibility(View.VISIBLE);
                        } else {
                            tvResult.setText("翻译失败或接口超时");
                        }
                    }
                });
            }
        }).start();
    }

    private String doTranslate(String text) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://deeplx.jayogo.com/translate/sk-D0TB8dagu1yxZoLrmOdenfcugyf82D14zTZoUUTRLoQFx9OJ").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");
            JSONObject j = new JSONObject(); j.put("text", text); j.put("source_lang", "auto"); j.put("target_lang", "ZH");
            c.getOutputStream().write(j.toString().getBytes("UTF-8"));
            if (c.getResponseCode()==200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder s = new StringBuilder(); String l;
                while((l=r.readLine())!=null) s.append(l);
                return new JSONObject(s.toString()).optString("data");
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (ballView != null && ballView.getParent()!=null) windowManager.removeView(ballView);
        if (windowView != null && windowView.getParent()!=null) windowManager.removeView(windowView);
        super.onDestroy();
    }
}