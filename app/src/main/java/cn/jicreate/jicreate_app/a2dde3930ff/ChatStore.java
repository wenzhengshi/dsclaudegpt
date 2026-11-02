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
    private static final int DB_VERSION = 3;

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static class Message {
        public final long id;
        public final String role;
        public final String content;
        public final long createdAt;
        /** 助手消息的思考过程（reasoning）；用户消息或旧数据为空串。 */
        public final String reasoning;
        /**
         * 是否为未完成的半成品回答（生成中被停止、断网或进程被回收）。
         * 重新打开会话后据此显示“继续生成”，不会把已有内容当成完整回答。
         */
        public final boolean partial;

        public Message(long id, String role, String content, long createdAt) {
            this(id, role, content, createdAt, "");
        }

        public Message(long id, String role, String content, long createdAt, String reasoning) {
            this(id, role, content, createdAt, reasoning, false);
        }

        public Message(long id, String role, String content, long createdAt,
                       String reasoning, boolean partial) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.partial = partial;
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
                + "reasoning TEXT NOT NULL DEFAULT '',"
                + "partial INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv "
                + "ON messages(conversation_id, id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v2 新增思考过程列：旧会话保留正文，只是没有可展开的思考内容。
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''");
            } catch (Exception ignored) {
                // 列已存在时忽略，避免升级过程反复失败。
            }
        }
        // v3 新增未完成标记：重启后仍能识别“已停止/已中断”的半成品回答。
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN partial INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
                // 列已存在时忽略，避免升级过程反复失败。
            }
        }
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
        return addMessage(conversationId, role, content, "");
    }

    public long addMessage(long conversationId, String role, String content, String reasoning) {
        return addMessage(conversationId, role, content, reasoning, false);
    }

    public long addMessage(long conversationId, String role, String content,
                           String reasoning, boolean partial) {
        if (conversationId <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("conversation_id", conversationId);
        values.put("role", role);
        values.put("content", content == null ? "" : content);
        values.put("reasoning", reasoning == null ? "" : reasoning);
        values.put("partial", partial ? 1 : 0);
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

    /**
     * 生成过程中增量保存：正文刚收到一半、应用被杀掉时也不会丢。
     * 助手消息在发起请求时就先插入空行，之后按这一行不断覆盖更新。
     */
    public void updateMessage(long messageId, String content, String reasoning) {
        updateMessage(messageId, content, reasoning, false);
    }

    public void updateMessage(long messageId, String content, String reasoning,
                              boolean partial) {
        if (messageId <= 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("content", content == null ? "" : content);
        values.put("reasoning", reasoning == null ? "" : reasoning);
        values.put("partial", partial ? 1 : 0);
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
                new String[]{"id", "role", "content", "created_at", "reasoning", "partial"},
                "conversation_id=?", new String[]{String.valueOf(conversationId)},
                null, null, "id ASC");
        try {
            while (cursor.moveToNext()) {
                String role = cursor.getString(1);
                String content = cursor.getString(2);
                String reasoning = cursor.getString(4);
                boolean partial = cursor.getInt(5) == 1;
                // 生成中途被杀掉时可能留下空的助手草稿行，读取时直接跳过。
                if (ROLE_ASSISTANT.equals(role)
                        && (content == null || content.isEmpty())
                        && (reasoning == null || reasoning.isEmpty())) {
                    continue;
                }
                result.add(new Message(cursor.getLong(0), role, content,
                        cursor.getLong(3), reasoning, partial));
            }
        } finally {
            cursor.close();
        }
        return result;
    }
}
