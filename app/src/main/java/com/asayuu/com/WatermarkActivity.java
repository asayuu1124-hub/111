package com.asayuu.com;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WatermarkActivity extends Activity {
    // 解析接口配置
    private String apiHost = "https://jx.72ke.vip/home/api?type=dsp&uid=5545149&key=f49bf86a5fb364d83cb57f1a59a1c3ac&url=";
    
    // UI 控件
    private EditText etUrl;
    private VideoView videoView;
    private ImageView imagePreview;
    private TextView tvTitle, tvImageIndex, tvSwipeHint;
    private View downloadConsole, llImageDownload, previewContainer, loadingLayout, videoControlLayout;
    private Button btnDownloadVideo, btnDownloadCurrent, btnDownloadAll;
    
    // 视频控制组件
    private ImageButton btnVideoPlay, btnVideoFullscreen;
    private SeekBar videoSeekBar;
    private boolean isFullscreen = false;
    
    // 进度条刷新 Handler
    private Handler videoHandler = new Handler(Looper.getMainLooper());
    private Runnable updateThread;

    // 数据存储
    private String videoUrl = "";
    private ArrayList<String> imageUrls = new ArrayList<String>();
    private int currentImageIndex = 0;
    private float startX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark);

        // 1. 初始化 UI 绑定
        etUrl = (EditText) findViewById(R.id.et_url);
        videoView = (VideoView) findViewById(R.id.video_view);
        imagePreview = (ImageView) findViewById(R.id.image_preview);
        tvTitle = (TextView) findViewById(R.id.tv_title);
        tvImageIndex = (TextView) findViewById(R.id.tv_image_index);
        tvSwipeHint = (TextView) findViewById(R.id.tv_swipe_hint);
        downloadConsole = findViewById(R.id.download_console);
        llImageDownload = findViewById(R.id.ll_image_download);
        previewContainer = findViewById(R.id.preview_container);
        loadingLayout = findViewById(R.id.loading_layout);
        
        videoControlLayout = findViewById(R.id.video_control_layout);
        btnVideoPlay = (ImageButton) findViewById(R.id.btn_video_play);
        btnVideoFullscreen = (ImageButton) findViewById(R.id.btn_video_fullscreen);
        videoSeekBar = (SeekBar) findViewById(R.id.video_seekbar);
        
        btnDownloadVideo = (Button) findViewById(R.id.btn_download_video);
        btnDownloadCurrent = (Button) findViewById(R.id.btn_download_current);
        btnDownloadAll = (Button) findViewById(R.id.btn_download_all);
        
        // 2. 处理 Intent 自动填入
        String autoUrl = getIntent().getStringExtra("auto_url");
        if (autoUrl != null && !autoUrl.isEmpty()) {
            etUrl.setText(autoUrl);
        }

        // 3. 初始化交互逻辑
        initAction();
    }

    private void initAction() {
        // --- 解析按钮 ---
        findViewById(R.id.btn_parse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = etUrl.getText().toString().trim();
                String cleanUrl = extractUrl(input);
                if (cleanUrl != null) {
                    new ParseTask().execute(cleanUrl);
                } else {
                    Toast.makeText(WatermarkActivity.this, "未检测到有效链接", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // --- 视频播放/暂停 ---
        btnVideoPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    btnVideoPlay.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    videoView.start();
                    btnVideoPlay.setImageResource(android.R.drawable.ic_media_pause);
                    startUpdateThread();
                }
            }
        });

        // --- 视频全屏/放大 ---
        btnVideoFullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) previewContainer.getLayoutParams();
                if (!isFullscreen) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.setMargins(0, 0, 0, 0);
                    lp.addRule(RelativeLayout.BELOW, 0);
                    lp.addRule(RelativeLayout.ABOVE, 0);
                    findViewById(R.id.ll_header).setVisibility(View.GONE);
                    findViewById(R.id.ll_input_card).setVisibility(View.GONE);
                    downloadConsole.setVisibility(View.GONE);
                } else {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.addRule(RelativeLayout.BELOW, R.id.ll_input_card);
                    lp.addRule(RelativeLayout.ABOVE, R.id.download_console);
                    int m = (int) (15 * getResources().getDisplayMetrics().density);
                    lp.setMargins(0, m, 0, m);
                    findViewById(R.id.ll_header).setVisibility(View.VISIBLE);
                    findViewById(R.id.ll_input_card).setVisibility(View.VISIBLE);
                    downloadConsole.setVisibility(View.VISIBLE);
                }
                previewContainer.setLayoutParams(lp);
                isFullscreen = !isFullscreen;
            }
        });

        // --- 【修正版】进度条拖动逻辑 ---
        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动，停止自动刷新，防止进度条“打架”
                if (videoHandler != null && updateThread != null) {
                    videoHandler.removeCallbacks(updateThread);
                }
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                // 手指松开时才执行跳转，确保网络视频加载稳定
                if (videoView != null) {
                    videoView.seekTo(seekBar.getProgress());
                    // 延迟 500ms 重启监控，给播放器缓冲时间
                    videoHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (videoView.isPlaying()) startUpdateThread();
                        }
                    }, 500);
                }
            }
        });

        // --- 视频准备就绪 ---
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                loadingLayout.setVisibility(View.GONE);
                videoSeekBar.setMax(videoView.getDuration());
                videoControlLayout.setVisibility(View.VISIBLE);
                videoView.start();
                btnVideoPlay.setImageResource(android.R.drawable.ic_media_pause);
                startUpdateThread();
            }
        });

        // --- 【新增】视频播放结束自动重置 ---
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                btnVideoPlay.setImageResource(android.R.drawable.ic_media_play);
                videoSeekBar.setProgress(0);
                if (videoHandler != null && updateThread != null) {
                    videoHandler.removeCallbacks(updateThread);
                }
                Toast.makeText(WatermarkActivity.this, "播放结束", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 图片触控与下载按钮 ---
        imagePreview.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (imageUrls.isEmpty()) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) startX = event.getX();
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float endX = event.getX();
                    if (startX - endX > 100) changeImage(1);
                    else if (endX - startX > 100) changeImage(-1);
                }
                return true;
            }
        });

        btnDownloadVideo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkPermissionAndDownload(videoUrl, "video"); }
        });
        btnDownloadCurrent.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if(!imageUrls.isEmpty()) checkPermissionAndDownload(imageUrls.get(currentImageIndex), "image"); }
        });
        btnDownloadAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { for (String url : imageUrls) checkPermissionAndDownload(url, "image"); }
        });
    }

    private void startUpdateThread() {
        if (updateThread != null) videoHandler.removeCallbacks(updateThread);
        updateThread = new Runnable() {
            @Override public void run() {
                if (videoView != null && videoView.isPlaying()) {
                    videoSeekBar.setProgress(videoView.getCurrentPosition());
                    videoHandler.postDelayed(this, 1000);
                }
            }
        };
        videoHandler.post(updateThread);
    }

    private void changeImage(int step) {
        if (imageUrls.isEmpty()) return;
        imagePreview.setImageResource(0);
        currentImageIndex = (currentImageIndex + step + imageUrls.size()) % imageUrls.size();
        tvImageIndex.setText((currentImageIndex + 1) + " / " + imageUrls.size());
        new ImageLoadTask(imagePreview).execute(imageUrls.get(currentImageIndex));
    }

    private class ParseTask extends AsyncTask<String, Void, String> {
        @Override protected void onPreExecute() {
            loadingLayout.setVisibility(View.VISIBLE);
            tvTitle.setText("解析中...");
            videoControlLayout.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);
            imagePreview.setVisibility(View.GONE);
            downloadConsole.setVisibility(View.GONE);
        }
        @Override protected String doInBackground(String... params) {
            try {
                String url = apiHost + URLEncoder.encode(params[0], "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    return sb.toString();
                }
            } catch (Exception e) {}
            return "";
        }
        @Override protected void onPostExecute(String result) {
            if (result.isEmpty()) { 
                loadingLayout.setVisibility(View.GONE);
                tvTitle.setText("解析失败"); 
                return; 
            }
            try {
                JSONObject json = new JSONObject(result);
                if (json.optInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    tvTitle.setText(data.optString("title"));
                    videoUrl = data.optString("video");
                    JSONArray imgs = data.optJSONArray("images");
                    downloadConsole.setVisibility(View.VISIBLE);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        videoView.setVisibility(View.VISIBLE);
                        videoView.setVideoPath(videoUrl);
                        btnDownloadVideo.setVisibility(View.VISIBLE);
                        llImageDownload.setVisibility(View.GONE);
                    } else if (imgs != null) {
                        loadingLayout.setVisibility(View.GONE);
                        imageUrls.clear();
                        for(int i=0; i<imgs.length(); i++) imageUrls.add(imgs.getString(i));
                        imagePreview.setVisibility(View.VISIBLE);
                        tvImageIndex.setVisibility(View.VISIBLE);
                        tvSwipeHint.setVisibility(imgs.length() > 1 ? View.VISIBLE : View.GONE);
                        btnDownloadVideo.setVisibility(View.GONE);
                        llImageDownload.setVisibility(View.VISIBLE);
                        changeImage(0);
                    }
                }
            } catch (Exception e) { loadingLayout.setVisibility(View.GONE); }
        }
    }

    private void checkPermissionAndDownload(String url, String type) {
        if (Build.VERSION.SDK_INT < 33 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        } else startDownload(url, type);
    }

    private void startDownload(String url, String type) {
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String ext = type.equals("video") ? ".mp4" : ".jpg";
        r.setDestinationInExternalPublicDir(type.equals("video") ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES, "Neo/Neo_" + System.currentTimeMillis() + ext);
        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        Toast.makeText(this, "正在下载...", Toast.LENGTH_SHORT).show();
    }

    private String extractUrl(String text) {
        Matcher m = Pattern.compile("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").matcher(text);
        return m.find() ? m.group() : null;
    }

    private class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        ImageView v; public ImageLoadTask(ImageView v) { this.v = v; }
        @Override protected Bitmap doInBackground(String... params) {
            try { return BitmapFactory.decodeStream(new URL(params[0]).openStream()); } catch (Exception e) { return null; }
        }
        @Override protected void onPostExecute(Bitmap b) { if (b != null) v.setImageBitmap(b); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoHandler != null && updateThread != null) videoHandler.removeCallbacks(updateThread);
    }
}