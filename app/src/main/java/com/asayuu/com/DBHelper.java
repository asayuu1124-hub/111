package com.asayuu.com;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "asayuu_user.db";
    private static final int DB_VERSION = 2; // 版本升級

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password TEXT)");
        db.execSQL("CREATE TABLE clipboard_history (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, time LONG)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_history (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, time LONG)");
        }
    }

    public boolean register(String user, String pass) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("username", user);
        cv.put("password", pass);
        long result = db.insert("users", null, cv);
        return result != -1;
    }

    public boolean checkUser(String user, String pass) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE username=? AND password=?", new String[]{user, pass});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    // --- 剪貼板歷史增強 ---
    public void addClip(String content) {
        if (content == null || content.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        // 刪除重複項保持唯一
        db.delete("clipboard_history", "content=?", new String[]{content});
        ContentValues cv = new ContentValues();
        cv.put("content", content);
        cv.put("time", System.currentTimeMillis());
        db.insert("clipboard_history", null, cv);
        // 僅保留最近 30 條
        db.execSQL("DELETE FROM clipboard_history WHERE id NOT IN (SELECT id FROM clipboard_history ORDER BY time DESC LIMIT 30)");
    }

    public List<String> getClips() {
        List<String> list = new ArrayList<String>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT content FROM clipboard_history ORDER BY time DESC", null);
        if (c.moveToFirst()) {
            do {
                list.add(c.getString(0));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }
}