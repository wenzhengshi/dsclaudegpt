package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天记录本地存储（SQLite）。会话和消息都只保存在设备本地。
 */
public class ChatStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "claude_chat.db";
    private static final int DB_VERSION = 1;

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static class Message {
        public final long id;
        public final String role;
        public final String content;
        public final long createdAt;

        public Message(long id, String role, String content, long createdAt) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    public static class Conversation {
        public final long id;
        public final String title;
        public final long updatedAt;
        public final boolean starred;

        public Conversation(long id, String title, long updatedAt, boolean starred) {
            this.id = id;
            this.title = title;
            this.updatedAt = updatedAt;
            this.starred = starred;
        }
    }

    public ChatStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "starred INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "conversation_id INTEGER NOT NULL,"
                + "role TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv "
                + "ON messages(conversation_id, id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 目前只有 v1，保留空实现避免误删用户数据。
    }

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------
    public long createConversation(String title) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("title", title == null || title.trim().isEmpty() ? "新对话" : title.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("starred", 0);
        return getWritableDatabase().insert("conversations", null, values);
    }

    public void renameConversation(long id, String title) {
        if (id <= 0 || title == null || title.trim().isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", values, "id=?",
                new String[]{String.valueOf(id)});
    }

    public void setStarred(long id, boolean starred) {
        if (id <= 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("starred", starred ? 1 : 0);
        getWritableDatabase().update("conversations", values, "id=?",
                new String[]{String.valueOf(id)});
    }

    public void touchConversation(long id) {
        if (id <= 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", values, "id=?",
                new String[]{String.valueOf(id)});
    }

    public Conversation getConversation(long id) {
        Cursor cursor = getReadableDatabase().query("conversations",
                new String[]{"id", "title", "updated_at", "starred"},
                "id=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return new Conversation(cursor.getLong(0), cursor.getString(1),
                        cursor.getLong(2), cursor.getInt(3) == 1);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public List<Conversation> listConversations() {
        List<Conversation> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("conversations",
                new String[]{"id", "title", "updated_at", "starred"},
                null, null, null, null, "starred DESC, updated_at DESC");
        try {
            while (cursor.moveToNext()) {
                result.add(new Conversation(cursor.getLong(0), cursor.getString(1),
                        cursor.getLong(2), cursor.getInt(3) == 1));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public void deleteConversation(long id) {
        if (id <= 0) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "conversation_id=?", new String[]{String.valueOf(id)});
        db.delete("conversations", "id=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", null, null);
        db.delete("conversations", null, null);
    }

    // ------------------------------------------------------------------
    // 消息
    // ------------------------------------------------------------------
    public long addMessage(long conversationId, String role, String content) {
        if (conversationId <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("conversation_id", conversationId);
        values.put("role", role);
        values.put("content", content == null ? "" : content);
        values.put("created_at", now);
        long id = getWritableDatabase().insert("messages", null, values);
        touchConversation(conversationId);
        return id;
    }

    public void updateMessage(long messageId, String content) {
        if (messageId <= 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("content", content == null ? "" : content);
        getWritableDatabase().update("messages", values, "id=?",
                new String[]{String.valueOf(messageId)});
    }

    public void deleteMessage(long messageId) {
        if (messageId <= 0) {
            return;
        }
        getWritableDatabase().delete("messages", "id=?",
                new String[]{String.valueOf(messageId)});
    }

    public void deleteMessagesFrom(long conversationId, long messageId) {
        if (conversationId <= 0 || messageId <= 0) {
            return;
        }
        getWritableDatabase().delete("messages", "conversation_id=? AND id>=?",
                new String[]{String.valueOf(conversationId), String.valueOf(messageId)});
    }

    public List<Message> loadMessages(long conversationId) {
        List<Message> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("messages",
                new String[]{"id", "role", "content", "created_at"},
                "conversation_id=?", new String[]{String.valueOf(conversationId)},
                null, null, "id ASC");
        try {
            while (cursor.moveToNext()) {
                result.add(new Message(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getLong(3)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }
}
