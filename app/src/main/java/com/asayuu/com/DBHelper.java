package com.asayuu.com;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "asayuu_user.db";
    private static final int DB_VERSION = 4; // 保持 4，僅新增方法

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password TEXT)");
        db.execSQL("CREATE TABLE clipboard_history (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, time LONG)");
        
        // 🧠 AI 神經網路對話相關
        db.execSQL("CREATE TABLE ai_chat_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, update_time LONG)");
        db.execSQL("CREATE TABLE ai_chat_history (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, role TEXT, content TEXT, time LONG)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_history (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, time LONG)");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ai_chat_history (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, content TEXT, time LONG)");
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ai_chat_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, update_time LONG)");
            try { db.execSQL("ALTER TABLE ai_chat_history ADD COLUMN session_id INTEGER DEFAULT 1"); } catch (Exception e) {}
            ContentValues cv = new ContentValues();
            cv.put("id", 1); cv.put("title", "初代神經鏈路"); cv.put("update_time", System.currentTimeMillis());
            db.insertWithOnConflict("ai_chat_sessions", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // --- 剪貼板與用戶邏輯 ---
    public boolean register(String user, String pass) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("username", user); cv.put("password", pass);
        return db.insert("users", null, cv) != -1;
    }

    public boolean checkUser(String user, String pass) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE username=? AND password=?", new String[]{user, pass});
        boolean exists = cursor.getCount() > 0;
        cursor.close(); return exists;
    }

    public void addClip(String content) {
        if (content == null || content.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("clipboard_history", "content=?", new String[]{content});
        ContentValues cv = new ContentValues();
        cv.put("content", content); cv.put("time", System.currentTimeMillis());
        db.insert("clipboard_history", null, cv);
        db.execSQL("DELETE FROM clipboard_history WHERE id NOT IN (SELECT id FROM clipboard_history ORDER BY time DESC LIMIT 30)");
    }

    public List<String> getClips() {
        List<String> list = new ArrayList<String>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT content FROM clipboard_history ORDER BY time DESC", null);
        if (c.moveToFirst()) { do { list.add(c.getString(0)); } while (c.moveToNext()); }
        c.close(); return list;
    }

    // --- 🧠 終端神經網路 (多對話 Session 版) ---

    public long createAiSession(String title) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("update_time", System.currentTimeMillis());
        return db.insert("ai_chat_sessions", null, cv);
    }

    public List<String[]> getAllSessions() {
        List<String[]> list = new ArrayList<String[]>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, title FROM ai_chat_sessions ORDER BY update_time DESC", null);
        if (c.moveToFirst()) {
            do { list.add(new String[]{c.getString(0), c.getString(1)}); } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    // ⭐ 新增：自定義修改會話名稱
    public void updateSessionTitle(long sessionId, String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", newTitle);
        db.update("ai_chat_sessions", cv, "id=?", new String[]{String.valueOf(sessionId)});
    }

    public void deleteSession(long sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("ai_chat_sessions", "id=?", new String[]{String.valueOf(sessionId)});
        db.delete("ai_chat_history", "session_id=?", new String[]{String.valueOf(sessionId)});
    }

    public void addChatMessage(long sessionId, String role, String content) {
        if (content == null || content.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        
        ContentValues cv = new ContentValues();
        cv.put("session_id", sessionId);
        cv.put("role", role);
        cv.put("content", content);
        cv.put("time", System.currentTimeMillis());
        db.insert("ai_chat_history", null, cv);

        ContentValues cvUpdate = new ContentValues();
        cvUpdate.put("update_time", System.currentTimeMillis());
        db.update("ai_chat_sessions", cvUpdate, "id=?", new String[]{String.valueOf(sessionId)});
    }

    public List<String[]> getChatHistoryBySession(long sessionId) {
        List<String[]> list = new ArrayList<String[]>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT role, content FROM ai_chat_history WHERE session_id=? ORDER BY time ASC", new String[]{String.valueOf(sessionId)});
        if (c.moveToFirst()) {
            do { list.add(new String[]{c.getString(0), c.getString(1)}); } while (c.moveToNext());
        }
        c.close();
        return list;
    }
}