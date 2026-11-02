package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用配置。API Key / 接口地址 / 模型等仅保存在本机 SharedPreferences 中，
 * 不会上传到任何服务器。
 */
public final class Prefs {

    private static final String NAME = "claude_local_prefs";

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-flash";
    public static final String FALLBACK_MODEL = "deepseek-chat";

    /** 界面风格：两套完整皮肤共用同一套 DeepSeek 接口与本地聊天库。 */
    public static final int STYLE_CLAUDE = 0;
    public static final int STYLE_CHATGPT = 1;
    public static final int STYLE_DEEPSEEK = 2;

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个乐于助人的 AI 助手。请使用简体中文回答，除非用户使用其他语言。"
                    + "回答要准确、清晰、有条理；涉及代码时给出可直接运行的完整示例。";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext()
                .getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // DeepSeek API
    // ------------------------------------------------------------------
    public String apiKey() {
        return sp.getString("api_key", "").trim();
    }

    public void setApiKey(String value) {
        sp.edit().putString("api_key", value == null ? "" : value.trim()).apply();
    }

    public boolean hasApiKey() {
        return apiKey().length() > 0;
    }

    public String baseUrl() {
        String value = sp.getString("base_url", DEFAULT_BASE_URL);
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? DEFAULT_BASE_URL : value;
    }

    public void setBaseUrl(String value) {
        sp.edit().putString("base_url", value == null ? "" : value.trim()).apply();
    }

    public String model() {
        String value = sp.getString("model", DEFAULT_MODEL);
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? DEFAULT_MODEL : value;
    }

    public void setModel(String value) {
        sp.edit().putString("model", value == null ? "" : value.trim()).apply();
    }

    // ------------------------------------------------------------------
    // /models 返回的模型列表缓存：设置页重新打开时可以立即展示上次结果。
    // ------------------------------------------------------------------
    public String cachedModels() {
        return sp.getString("models_cache", "");
    }

    public void setCachedModels(String value) {
        sp.edit().putString("models_cache", value == null ? "" : value).apply();
    }

    public String systemPrompt() {
        String value = sp.getString("system_prompt", DEFAULT_SYSTEM_PROMPT);
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? DEFAULT_SYSTEM_PROMPT : value;
    }

    public void setSystemPrompt(String value) {
        sp.edit().putString("system_prompt", value == null ? "" : value.trim()).apply();
    }

    // ------------------------------------------------------------------
    // 界面偏好
    // ------------------------------------------------------------------
    /** 附件面板展示版本：版本号变化时允许再自动展示一次，方便升级后确认面板形态。 */
    private static final int ATTACH_INTRO_VERSION = 4;

    /** 是否已经按当前版本展示过附件面板（用于把参考图的默认画面呈现给用户）。 */
    public boolean attachIntroShown() {
        return sp.getInt("attach_intro_version", 0) >= ATTACH_INTRO_VERSION;
    }

    public void setAttachIntroShown(boolean value) {
        sp.edit().putInt("attach_intro_version", value ? ATTACH_INTRO_VERSION : 0).apply();
    }

    /** 会话标题栏首次展示版本：升级后首次启动进入会话加载态，方便直接看到本轮标题栏。 */
    private static final int SESSION_NAV_INTRO_VERSION = 1;

    public boolean sessionNavIntroShown() {
        return sp.getInt("session_nav_intro_version", 0) >= SESSION_NAV_INTRO_VERSION;
    }

    public void setSessionNavIntroShown(boolean value) {
        sp.edit().putInt("session_nav_intro_version",
                value ? SESSION_NAV_INTRO_VERSION : 0).apply();
    }

    public boolean soundOn() {
        return sp.getBoolean("sound_on", true);
    }

    public void setSoundOn(boolean value) {
        sp.edit().putBoolean("sound_on", value).apply();
    }

    public boolean hapticOn() {
        return sp.getBoolean("haptic_on", false);
    }

    public void setHapticOn(boolean value) {
        sp.edit().putBoolean("haptic_on", value).apply();
    }

    public boolean extendOn() {
        return sp.getBoolean("extend_on", false);
    }

    public void setExtendOn(boolean value) {
        sp.edit().putBoolean("extend_on", value).apply();
    }

    public String fullModel() {
        String value = sp.getString("full_model", "Haiku 5.5");
        return value == null || value.isEmpty() ? "Haiku 5.5" : value;
    }

    public void setFullModel(String value) {
        sp.edit().putString("full_model", value).apply();
    }

    public String compactModel() {
        String value = sp.getString("compact_model", "Haiku");
        return value == null || value.isEmpty() ? "Haiku" : value;
    }

    public void setCompactModel(String value) {
        sp.edit().putString("compact_model", value).apply();
    }

    // ------------------------------------------------------------------
    // 界面风格：0 = Claude，1 = ChatGPT，2 = DeepSeek App
    // ------------------------------------------------------------------
    public int uiStyle() {
        // 新安装默认使用本轮参考图对应的 ChatGPT 风格；
        // 已经选择过其它风格的用户保持原选择。
        int value = sp.getInt("ui_style", STYLE_CHATGPT);
        if (value == STYLE_CLAUDE || value == STYLE_CHATGPT || value == STYLE_DEEPSEEK) {
            return value;
        }
        return STYLE_CHATGPT;
    }

    public void setUiStyle(int style) {
        sp.edit().putInt("ui_style",
                style == STYLE_CLAUDE || style == STYLE_DEEPSEEK
                        ? style : STYLE_CHATGPT).apply();
    }

    // ------------------------------------------------------------------
    // 思考（reasoning）相关配置：三套皮肤共用同一份开关与强度。
    // 开关会真正作为请求参数发送，而不是只改系统提示词。
    // ------------------------------------------------------------------
    public static final int THINK_LEVEL_LOW = 0;
    public static final int THINK_LEVEL_MEDIUM = 1;
    public static final int THINK_LEVEL_HIGH = 2;

    public boolean thinkOn() {
        return sp.getBoolean("ds_think_on", false);
    }

    public void setThinkOn(boolean value) {
        sp.edit().putBoolean("ds_think_on", value).apply();
    }

    /** 思考强度：低 / 中 / 高，默认高，与界面上的“高”保持一致。 */
    public int thinkLevel() {
        int value = sp.getInt("think_level", THINK_LEVEL_HIGH);
        return value == THINK_LEVEL_LOW || value == THINK_LEVEL_MEDIUM
                ? value : THINK_LEVEL_HIGH;
    }

    public void setThinkLevel(int value) {
        int level = value == THINK_LEVEL_LOW || value == THINK_LEVEL_MEDIUM
                ? value : THINK_LEVEL_HIGH;
        sp.edit().putInt("think_level", level).apply();
    }

    /** 发送给 OpenAI 兼容接口的 reasoning_effort 取值。 */
    public static String thinkLevelApiValue(int level) {
        if (level == THINK_LEVEL_LOW) {
            return "low";
        }
        if (level == THINK_LEVEL_MEDIUM) {
            return "medium";
        }
        return "high";
    }

    /** 是否在回答上方展示思考过程（默认展示）。 */
    public boolean showThought() {
        return sp.getBoolean("show_thought", true);
    }

    public void setShowThought(boolean value) {
        sp.edit().putBoolean("show_thought", value).apply();
    }

    public boolean searchOn() {
        return sp.getBoolean("ds_search_on", false);
    }

    public void setSearchOn(boolean value) {
        sp.edit().putBoolean("ds_search_on", value).apply();
    }

    // ------------------------------------------------------------------
    // 桌面图标：0 为默认的「蓝豚王冠」，其余对应 manifest 中的 activity-alias
    // ------------------------------------------------------------------
    public int iconStyle() {
        int value = sp.getInt("icon_style", 0);
        // 旧版本内置顺序是「蓝鲸、缠绕几何、星芒」，新版在 0 位插入了默认的
        // 「蓝豚王冠」。升级时把旧的 1、2 号选择向后平移，避免用户已选的图标被换掉；
        // 旧的 0 号沿用 0 位，正好显示本轮新增的默认图标。
        if (!sp.getBoolean("icon_style_slot_v2", false)) {
            if (sp.contains("icon_style")) {
                if (value == 1) {
                    value = 2;
                } else if (value == 2) {
                    value = 3;
                }
                sp.edit().putInt("icon_style", value).apply();
            }
            sp.edit().putBoolean("icon_style_slot_v2", true).apply();
        }
        return value;
    }

    public void setIconStyle(int value) {
        sp.edit().putInt("icon_style", Math.max(0, value)).apply();
    }
}
