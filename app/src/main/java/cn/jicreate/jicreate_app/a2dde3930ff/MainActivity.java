package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Icon;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_INCOGNITO = 1;
    private static final int SCREEN_VOICE = 2;
    private static final int SCREEN_CHAT = 3;

    private static final int SHEET_NONE = 0;
    private static final int SHEET_FULL = 1;
    private static final int SHEET_COMPACT = 2;
    private static final int SHEET_SETTINGS = 3;
    private static final int SHEET_ATTACH = 4;
    private static final int SHEET_ICON = 5;

    private static final int REQUEST_PICK_IMAGE = 1003;
    private static final int REQUEST_PICK_DOCUMENT = 1004;
    private static final int REQUEST_TAKE_PHOTO = 1005;
    private static final int REQUEST_PICK_LOGO = 1002;
    private static final int REQUEST_PICK_APP_ICON = 1006;
    private static final int HOME_LOGO_MAX_SIDE = 720;
    private static final int APP_ICON_MAX_SIDE = 512;

    /**
     * 内置桌面图标：顺序与 manifest 中的启动入口（默认 Activity + activity-alias）对应。
     * 切换时只启用其中一个，即可替换手机桌面上显示的应用图标。
     */
    private static final String[] ICON_COMPONENT_SUFFIX = {
            ".LaunchEntryActivity", ".IconStyleWhale", ".IconStyleInterlace", ".IconStyleStarburst"
    };
    private static final int[] ICON_PREVIEWS = {
            R.drawable.ic_icon_preview_dolphin,
            R.drawable.ic_icon_preview_whale,
            R.drawable.ic_icon_preview_interlace,
            R.drawable.ic_icon_preview_starburst
    };
    private static final int[] ICON_NAME_RES = {
            R.string.icon_style_dolphin, R.string.icon_style_whale,
            R.string.icon_style_interlace, R.string.icon_style_starburst
    };
    private static final int[] ICON_CELL_BOX_IDS = {
            R.id.icon_cell_box_0, R.id.icon_cell_box_1, R.id.icon_cell_box_2, R.id.icon_cell_box_3
    };
    private static final int[] ICON_CELL_NAME_IDS = {
            R.id.icon_cell_name_0, R.id.icon_cell_name_1, R.id.icon_cell_name_2, R.id.icon_cell_name_3
    };
    private static final int[] ICON_CELL_IDS = {
            R.id.icon_cell_0, R.id.icon_cell_1, R.id.icon_cell_2, R.id.icon_cell_3
    };
    private static final String SHORTCUT_ID_PHOTO_ICON = "claude_photo_icon";

    /** 桌面图标长按快捷方式：action 与 extra 名称需与 res/xml/shortcuts.xml 保持一致。 */
    public static final String ACTION_OPEN_STYLE =
            "cn.jicreate.jicreate_app.a2dde3930ff.action.OPEN_STYLE";
    public static final String EXTRA_UI_STYLE =
            "cn.jicreate.jicreate_app.a2dde3930ff.extra.UI_STYLE";

    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_ATTACHMENT_COPY_BYTES = 20L * 1024 * 1024;
    private static final long MAX_TEXT_COPY_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 1440;
    private static final int THUMB_SIDE = 240;
    private static final String ATTACH_MARK = "[[claude-attachment]]";

    private static final String[] FULL_MODELS = {"Fable 5.1", "Opus 5.5", "Sonnet 5.5", "Haiku 5.5"};
    private static final String[] COMPACT_MODELS = {"Fable", "Sonnet", "Haiku"};
    /** 皮肤对应的展示名；内部仍保存 FULL_MODELS / COMPACT_MODELS，避免丢失既有选择。 */
    private static final String[] DS_FULL_LABELS = {"DeepSeek V3.2", "DeepSeek R1",
            "DeepSeek Coder", "DeepSeek Lite"};
    private static final String[] DS_COMPACT_LABELS = {"V3.2", "R1", "Lite"};
    private static final String[] GPT_FULL_LABELS = {"GPT-5.1", "GPT-5", "GPT-4.1", "GPT-4o mini"};
    private static final String[] GPT_COMPACT_LABELS = {"GPT-5", "GPT-4.1", "4o mini"};

    private static final int MAX_HISTORY_TURNS = 30;
    private static final int MAX_TURN_CHARS = 12000;
    private static final int MAX_ATTACHMENT_BYTES = 200 * 1024;
    private static final int MAX_ATTACHMENT_PROMPT_CHARS = 8000;
    /** 单次请求最多携带的历史图片数（优先最近的消息，控制请求体大小）。 */
    private static final int MAX_API_IMAGES = 8;
    /** 单张图片原始字节上限，DeepSeek 单图限制为 32 MiB，这里留出更保守的余量。 */
    private static final long MAX_API_IMAGE_BYTES = 10L * 1024 * 1024;
    /** 一次请求中所有图片原始字节总量上限（base64 后约膨胀 4/3）。 */
    private static final long MAX_API_IMAGE_TOTAL_BYTES = 24L * 1024 * 1024;

    private FrameLayout sheetLayer;
    private FrameLayout sheetHost;
    private FrameLayout drawerLayer;
    private DrawerSwipeLayout drawerSwipe;
    private View drawerPanel;
    private View drawerScrim;
    private View sheetScrim;

    private View homeView;
    private View incognitoView;
    private View voiceView;
    private View chatView;

    private TextView homeModelView;
    private TextView incognitoModelView;
    private TextView voiceModelView;
    private TextView chatModelView;
    private EditText homeInput;
    private EditText incognitoInput;
    private ImageButton homeComposerAction;
    private ImageButton incognitoComposerAction;

    private ScrollView chatScroll;
    private LinearLayout chatMessages;
    private TextView chatTitleView;
    private EditText chatInput;
    private ImageButton chatAction;

    private View voiceConnecting;
    private View voiceFailed;
    private View voiceErrorCard;

    // ChatGPT 语音页的天空语音球（参考本轮 HTML 的 WebGL 球体）
    private View voiceOrbWrap;
    private SkyOrbView voiceOrb;
    private OrbHaloView voiceOrbHalo;
    private View voiceOrbFallback;

    private View fullSheet;
    private View compactSheet;

    // 输入框上方的待发送附件预览
    private HorizontalScrollView homeAttachStrip;
    private HorizontalScrollView chatAttachStrip;
    private HorizontalScrollView incognitoAttachStrip;
    private LinearLayout homeAttachList;
    private LinearLayout chatAttachList;
    private LinearLayout incognitoAttachList;
    private final List<Attachment> pendingAttachments = new ArrayList<>();
    private final Map<String, Bitmap> thumbCache = new HashMap<>();
    private File cameraOutputFile;
    private boolean webSearchOn = true;

    private int currentScreen = SCREEN_HOME;
    private int sheetMode = SHEET_NONE;
    private boolean drawerOpen = false;
    private boolean soundOn = true;
    private boolean hapticOn = false;
    private boolean extendOn = false;
    private boolean audioMuted = false;
    private int voiceReturnScreen = SCREEN_HOME;

    private String fullSelected = "Haiku 5.5";
    private String compactSelected = "Haiku";

    private Prefs prefs;
    private ChatStore store;
    private DeepSeekClient client;

    /** 当前界面风格；三套皮肤共用同一套 DeepSeek 与本地聊天数据。 */
    private boolean gptStyle = false;
    private boolean dsStyle = false;
    private GptHomeNavView gptHomeNav;
    private View gptEmptyWork;
    private Runnable gptHomeToastHide;
    private EditText drawerSearchInput;

    // DeepSeek 风格：深度思考开关与本次回复开始时间（用于“已思考（用时 N 秒）”）
    private boolean thinkOn = false;
    private long replyStartedAt = 0L;

    /** 当前会话在本地数据库中的 id，0 表示尚未落库。 */
    private long currentConversationId = 0L;
    private boolean incognitoMode = false;

    private static class Turn {
        final String role;
        final String text;
        final long dbId;
        final List<Attachment> attachments;

        Turn(String role, String text, long dbId) {
            this(role, text, dbId, null);
        }

        Turn(String role, String text, long dbId, List<Attachment> attachments) {
            this.role = role;
            this.text = text == null ? "" : text;
            this.dbId = dbId;
            this.attachments = attachments == null
                    ? new ArrayList<>() : new ArrayList<>(attachments);
        }
    }

    /**
     * 本地附件。图片与文本文件会复制到应用私有目录；
     * 二进制文件只记录元信息，不会上传内容（DeepSeek 文本接口不支持文件上传）。
     */
    private static class Attachment {
        String kind;      // image / file
        String name;
        String mime;
        long size;
        int width;
        int height;
        String path;      // 应用私有目录中的本地副本，可能为空
        String text;      // 文本文件正文，仅运行时使用
        boolean loading;

        boolean isImage() {
            return "image".equals(kind);
        }
    }

    private final List<Turn> history = new ArrayList<>();

    // Chat state
    private boolean chatSending = false;
    private View aiRow;
    private TextView aiBody;
    private View aiActions;
    private View aiDisclaimer;
    private View loadingRow;
    private View errorRow;
    private int aiTurnIndex = -1;
    private final StringBuilder streamBuffer = new StringBuilder();
    private String streamingText = "";
    private boolean flushScheduled = false;
    private boolean modelSwitchedNotified = false;
    private boolean imagesDroppedNotified = false;

    private PopupWindow chatMenuPopup;
    private GptChatNavController gptNavController;
    private boolean sessionNavIntroMode = false;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private ImageButton speakingButton;
    private DsActionsView speakingActions;


    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable connectionFailure = new Runnable() {
        @Override
        public void run() {
            if (currentScreen != SCREEN_VOICE) {
                return;
            }
            voiceConnecting.setVisibility(View.GONE);
            voiceFailed.setVisibility(View.VISIBLE);
            voiceErrorCard.setVisibility(View.VISIBLE);
        }
    };

    private final Runnable streamFlush = new Runnable() {
        @Override
        public void run() {
            flushScheduled = false;
            if (!chatSending || aiBody == null) {
                return;
            }
            streamingText = streamBuffer.toString();
            aiBody.setText(streamingText);
            scrollChatToBottom();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupSystemBars();

        prefs = new Prefs(this);
        // 桌面长按快捷方式可能指定了要进入的风格：先落盘再装配界面，
        // 这样冷启动时直接就是用户点选的那套皮肤。
        applyUiStyleFromIntent(getIntent());
        gptStyle = prefs.uiStyle() == Prefs.STYLE_CHATGPT;
        dsStyle = prefs.uiStyle() == Prefs.STYLE_DEEPSEEK;
        thinkOn = prefs.thinkOn();
        webSearchOn = prefs.searchOn();
        store = new ChatStore(this);
        client = new DeepSeekClient();

        soundOn = prefs.soundOn();
        hapticOn = prefs.hapticOn();
        extendOn = prefs.extendOn();
        fullSelected = prefs.fullModel();
        compactSelected = prefs.compactModel();
        syncIconStyle();

        FrameLayout screenContainer = findViewById(R.id.screen_container);
        drawerSwipe = findViewById(R.id.root);
        drawerLayer = findViewById(R.id.drawer_layer);
        sheetLayer = findViewById(R.id.sheet_layer);
        sheetHost = findViewById(R.id.sheet_host);
        drawerPanel = findViewById(R.id.drawer_panel);
        drawerScrim = findViewById(R.id.drawer_scrim);
        sheetScrim = findViewById(R.id.sheet_scrim);

        LayoutInflater inflater = LayoutInflater.from(this);
        if (gptStyle || dsStyle) {
            // ChatGPT 风格根背景 #FCFCFC；DeepSeek 风格是参考图实测的纯白 #FFFFFF。
            View root = findViewById(R.id.root);
            int bgRes = gptStyle ? R.color.gpt_bg : R.color.ds_bg;
            root.setBackgroundColor(ContextCompat.getColor(this, bgRes));
            getWindow().setBackgroundDrawable(new ColorDrawable(
                    ContextCompat.getColor(this, bgRes)));
            drawerScrim.setBackgroundColor(ContextCompat.getColor(this,
                    gptStyle ? R.color.gpt_scrim : R.color.ds_scrim));
        }

        // 三套皮肤共用抽屉视图，但滑动手势策略不同：
        // Claude 仅按钮控制，ChatGPT 仅左缘滑开，DeepSeek 全屏可滑且主内容跟手右移。
        int drawerMode = dsStyle ? DrawerSwipeLayout.MODE_FOLLOW
                : (gptStyle ? DrawerSwipeLayout.MODE_EDGE : DrawerSwipeLayout.MODE_BUTTON_ONLY);
        drawerSwipe.bind(drawerLayer, drawerPanel, drawerScrim, screenContainer, drawerMode);
        drawerSwipe.setOnDrawerListener(new DrawerSwipeLayout.OnDrawerListener() {
            @Override
            public void onDrawerOpened() {
                drawerOpen = true;
            }

            @Override
            public void onDrawerClosed() {
                drawerOpen = false;
            }
        });

        // 抽屉内容按风格动态装配，外层容器宽度仍由 resizeDrawer() 控制。
        View drawerContent = inflater.inflate(styleLayout(R.layout.view_drawer,
                R.layout.view_drawer_gpt, R.layout.view_drawer_ds),
                (ViewGroup) drawerPanel, false);
        // 抽屉空白区域的点击不应穿透到遮罩上，否则点面板也会误关闭。
        drawerContent.setClickable(true);
        ((ViewGroup) drawerPanel).addView(drawerContent,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        homeView = inflater.inflate(styleLayout(R.layout.view_home,
                R.layout.view_home_gpt, R.layout.view_home_ds), screenContainer, false);
        incognitoView = inflater.inflate(styleLayout(R.layout.view_incognito,
                R.layout.view_incognito_gpt, R.layout.view_incognito_ds),
                screenContainer, false);
        voiceView = inflater.inflate(styleLayout(R.layout.view_voice,
                R.layout.view_voice_gpt, R.layout.view_voice_ds), screenContainer, false);
        chatView = inflater.inflate(styleLayout(R.layout.view_chat,
                R.layout.view_chat_gpt, R.layout.view_chat_ds), screenContainer, false);
        if (gptStyle) {
            // 会话页使用标题栏参考稿的整页横向渐变，让顶栏与正文之间没有色差接缝。
            chatView.setBackground(new GptNavBackdropDrawable());
        }
        screenContainer.addView(homeView);
        screenContainer.addView(incognitoView);
        screenContainer.addView(voiceView);
        screenContainer.addView(chatView);

        setupHome();
        setupIncognito();
        setupVoice();
        setupChat();
        setupDrawer();
        setupSheetLayer();
        setupAttachmentViews();
        applyFullModel(fullSelected);
        applyCompactModel(compactSelected);
        showHome();
        refreshHomeApiHint();

        installSystemBarInsets();
        initTts();
        drawerLayer.post(this::resizeDrawer);

        // 首屏就是空对话首页：本轮参考稿的“菜单 + 聊天/工作 + 消息”标题栏与空态，
        // 会话标题栏仍保留在打开真实对话后的 chatView 中。
        if (gptStyle) {
            prefs.setAttachIntroShown(true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 只刷新系统栏外观，不重复设置 SOFT_INPUT / decorFits。
            // 键盘弹出和收起本身会让窗口焦点变化，若在这里反复重设输入模式，
            // 输入框会在点击后被重新布局，出现“点不进去/聚焦不了”的现象。
            applySystemBarAppearance();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Activity 被复用时同样应用快捷方式指定的风格；
        // 风格变化需要整页重建，否则只回到首页。
        setIntent(intent);
        if (applyUiStyleFromIntent(intent)) {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
            return;
        }
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 通过桌面快捷方式切换风格后，任务栈里可能还留着旧风格的实例。
        // 旧实例回到前台时按最新偏好重建，避免新旧界面对不上。
        if (prefs != null && prefs.uiStyle() != currentUiStyle()) {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
            return;
        }
        // 从后台/设置页返回时，确保输入框没有被遗留在禁用状态，且保留用户草稿
        if (chatInput != null) {
            restoreComposer();
        }
        refreshHomeApiHint();
    }

    /** 当前 Activity 正在展示的风格。 */
    private int currentUiStyle() {
        return dsStyle ? Prefs.STYLE_DEEPSEEK
                : (gptStyle ? Prefs.STYLE_CHATGPT : Prefs.STYLE_CLAUDE);
    }

    /** 读取快捷方式传入的风格值；缺省、类型不符或越界时返回 -1。 */
    private int intentUiStyle(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return -1;
        }
        Object raw = intent.getExtras().get(EXTRA_UI_STYLE);
        int style = -1;
        if (raw instanceof Number) {
            style = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                style = Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                style = -1;
            }
        }
        return (style == Prefs.STYLE_CLAUDE || style == Prefs.STYLE_CHATGPT
                || style == Prefs.STYLE_DEEPSEEK) ? style : -1;
    }

    /** 把快捷方式指定的风格写入本地偏好；返回是否真的发生了风格变化。 */
    private boolean applyUiStyleFromIntent(Intent intent) {
        int style = intentUiStyle(intent);
        if (style < 0 || prefs.uiStyle() == style) {
            return false;
        }
        prefs.setUiStyle(style);
        return true;
    }

    /**
     * 保持状态栏和导航栏可见：系统栏透明，由页面浅色背景填充，
     * 图标切换为深色，避免全屏隐藏系统栏后顶部出现黑屏/黑条。
     */
    private void setupSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        // 采用 edge-to-edge 后，Android 11+ 不再让系统同时把窗口缩到键盘上方，
        // 否则窗口 resize 与下面的 IME padding 会叠加，输入框会被顶出屏幕。
        // 因此新版本交给 WindowInsets 的 ime() 处理，旧版本继续依赖 adjustResize。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        applySystemBarAppearance();
    }

    /** 只刷新系统栏颜色与图标，不触碰输入法模式，避免键盘显示时布局反复重算。 */
    private void applySystemBarAppearance() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 关闭系统在透明系统栏上叠加的对比度遮罩，避免浅色背景发灰发黑
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
        }
    }

    /**
     * 内容延伸到系统栏之下，同时给根布局留出状态栏、导航栏和输入法的安全距离，
     * 保证状态栏区域始终显示页面背景色，输入框不会被导航栏或键盘遮挡。
     */
    private void installSystemBarInsets() {
        final View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottom = bars.bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = Math.max(bottom, imeBottom);
            }
            if (v.getPaddingLeft() != bars.left || v.getPaddingTop() != bars.top
                    || v.getPaddingRight() != bars.right || v.getPaddingBottom() != bottom) {
                v.setPadding(bars.left, bars.top, bars.right, bottom);
                if (currentScreen == SCREEN_CHAT) {
                    scrollChatToBottom();
                }
                if (sheetMode == SHEET_SETTINGS) {
                    // 键盘弹出后会挤占底部空间，设置面板需要同步收窄，避免顶部输入框被顶出屏幕
                    adjustSheetHeightForIme();
                }
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void resizeDrawer() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        // 参考图 29729 实测：侧边栏与内容区的纵向分割线在 x=598/767≈78% 处，
        // DeepSeek 与 ChatGPT 抽屉取这一比例；Claude 沿用原有的 94% 宽抽屉。
        float ratio = (dsStyle || gptStyle) ? 0.78f : 0.94f;
        int width = (int) (screenWidth * ratio);
        ViewGroup.LayoutParams params = drawerPanel.getLayoutParams();
        params.width = width;
        drawerPanel.setLayoutParams(params);
        if (drawerSwipe != null) {
            drawerSwipe.setDrawerWidth(width);
        }
    }

    /** 三套皮肤对应三份布局，公共逻辑仍复用同一批控件 id。 */
    private int styleLayout(int claude, int gpt, int deepseek) {
        if (dsStyle) {
            return deepseek;
        }
        return gptStyle ? gpt : claude;
    }

    // ---------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------
    private void setupHome() {
        // ChatGPT 空对话首页使用自绘标题栏（GptHomeNavView），其余皮肤仍是独立按钮。
        View homeMenu = homeView.findViewById(R.id.home_menu);
        if (homeMenu != null) {
            homeMenu.setOnClickListener(v -> openDrawer());
        }
        // 参考图右上角是圆形对话气泡入口，点击开始一段新对话。
        View homeGhost = homeView.findViewById(R.id.home_ghost);
        if (homeGhost != null) {
            homeGhost.setOnClickListener(v -> {
                resetConversation();
                showHome();
                if (homeInput != null) {
                    homeInput.requestFocus();
                }
            });
        }
        homeView.findViewById(R.id.home_api_hint).setOnClickListener(v -> openSheet(SHEET_SETTINGS));

        homeModelView = homeView.findViewById(R.id.home_model);
        homeModelView.setOnClickListener(v -> openSheet(SHEET_FULL));

        // 起始页 Logo 长按可从本地相册替换，替换后的照片会保存到应用私有目录
        ImageView logo = homeView.findViewById(R.id.home_star);
        if (logo != null) {
            logo.setOnLongClickListener(v -> {
                showHomeLogoOptions();
                return true;
            });
            applySavedHomeLogo(logo);
        }

        homeInput = homeView.findViewById(R.id.home_input);
        ImageButton action = homeView.findViewById(R.id.home_send);
        homeComposerAction = action;
        bindComposer(homeInput, action, homeView.findViewById(R.id.home_attach), false);
        homeView.findViewById(R.id.home_mic).setOnClickListener(v -> {
            hideKeyboard();
            showVoice();
        });
        if (gptStyle) {
            setupGptHome();
        }
        if (dsStyle) {
            setupDsHome();
        }
    }

    // ---------------------------------------------------------------------
    // DeepSeek App 风格：首页胶囊开关、喇叭与新建入口
    // ---------------------------------------------------------------------
    private void setupDsHome() {
        bindDsPill(homeView.findViewById(R.id.home_think), true);
        bindDsPill(homeView.findViewById(R.id.home_search), false);

        View speaker = homeView.findViewById(R.id.home_speaker);
        if (speaker != null) {
            speaker.setOnClickListener(v -> toggleSpeaker());
        }
        View newChat = homeView.findViewById(R.id.home_new);
        if (newChat != null) {
            newChat.setOnClickListener(v -> {
                resetConversation();
                showHome();
                if (homeInput != null) {
                    homeInput.requestFocus();
                }
            });
        }
        // 参考图的提示是“发消息或按住说话”：长按输入框直接进入语音页。
        if (homeInput != null) {
            homeInput.setOnLongClickListener(v -> {
                hideKeyboard();
                showVoice();
                return true;
            });
        }
        applyDsPills();
    }

    private void bindDsPill(View pill, final boolean isThink) {
        if (pill == null) {
            return;
        }
        pill.setOnClickListener(v -> {
            if (isThink) {
                toggleThink();
            } else {
                toggleDsSearch();
            }
        });
    }

    /** 首页与对话页的“深度思考 / 智能搜索”胶囊共用同一份开关状态。 */
    private void applyDsPills() {
        applyDsPill(homeView, "home_", thinkOn, webSearchOn);
        applyDsPill(chatView, "chat_", thinkOn, webSearchOn);
    }

    private void applyDsPill(View root, String prefix, boolean think, boolean search) {
        if (root == null) {
            return;
        }
        int thinkId = "home_".equals(prefix) ? R.id.home_think : R.id.chat_think;
        int thinkIconId = "home_".equals(prefix)
                ? R.id.home_think_icon : R.id.chat_think_icon;
        int thinkTextId = "home_".equals(prefix)
                ? R.id.home_think_text : R.id.chat_think_text;
        int searchId = "home_".equals(prefix) ? R.id.home_search : R.id.chat_search;
        int searchIconId = "home_".equals(prefix)
                ? R.id.home_search_icon : R.id.chat_search_icon;
        int searchTextId = "home_".equals(prefix)
                ? R.id.home_search_text : R.id.chat_search_text;

        View thinkPill = root.findViewById(thinkId);
        View searchPill = root.findViewById(searchId);
        if (thinkPill == null || searchPill == null) {
            return;
        }
        // 参考稿中两个模式按钮无论是否选中，文字与图标都保持品牌浅蓝；
        // 选中态只把底色和描边加深一档。
        int modeColor = ContextCompat.getColor(this, R.color.ds_composer_blue);

        thinkPill.setBackgroundResource(think
                ? R.drawable.bg_ds_composer_mode_active
                : R.drawable.bg_ds_composer_mode);
        ImageView thinkIcon = root.findViewById(thinkIconId);
        if (thinkIcon != null) {
            thinkIcon.setColorFilter(modeColor);
        }
        TextView thinkText = root.findViewById(thinkTextId);
        if (thinkText != null) {
            thinkText.setTextColor(modeColor);
        }

        searchPill.setBackgroundResource(search
                ? R.drawable.bg_ds_composer_mode_active
                : R.drawable.bg_ds_composer_mode);
        ImageView searchIcon = root.findViewById(searchIconId);
        if (searchIcon != null) {
            searchIcon.setColorFilter(modeColor);
        }
        TextView searchText = root.findViewById(searchTextId);
        if (searchText != null) {
            searchText.setTextColor(modeColor);
        }
    }

    private void toggleThink() {
        toggleThink(true);
    }

    private void toggleThink(boolean announce) {
        thinkOn = !thinkOn;
        prefs.setThinkOn(thinkOn);
        applyDsPills();
        if (!announce) {
            return;
        }
        int tip;
        if (gptStyle) {
            tip = thinkOn ? R.string.gpt_think_tip_on : R.string.gpt_think_tip_off;
        } else {
            tip = thinkOn ? R.string.ds_think_tip_on : R.string.ds_think_tip_off;
        }
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
    }

    private void toggleDsSearch() {
        webSearchOn = !webSearchOn;
        prefs.setSearchOn(webSearchOn);
        applyDsPills();
        Toast.makeText(this, webSearchOn ? R.string.ds_search_tip_on : R.string.ds_search_tip_off,
                Toast.LENGTH_SHORT).show();
    }

    private void toggleSpeaker() {
        soundOn = !soundOn;
        prefs.setSoundOn(soundOn);
        Toast.makeText(this, soundOn ? R.string.ds_speaker_on : R.string.ds_speaker_off,
                Toast.LENGTH_SHORT).show();
    }

    /** ChatGPT 空对话首页：自绘标题栏、工作建议入口。 */
    private void setupGptHome() {
        gptEmptyWork = homeView.findViewById(R.id.gpt_empty_work);
        gptHomeNav = homeView.findViewById(R.id.gpt_home_nav);
        if (gptHomeNav != null) {
            gptHomeNav.setListener(new GptHomeNavView.Listener() {
                @Override
                public void onMenuClick() {
                    openDrawer();
                }

                @Override
                public void onTabSelected(boolean chatTab) {
                    applyGptHomeTab(chatTab);
                }

                @Override
                public void onMessageClick() {
                    showGptHomeToast(getString(R.string.gpt_home_no_message));
                }
            });
        }
        bindGptWorkSuggestion(R.id.gpt_work_github, R.string.gpt_work_github);
        bindGptWorkSuggestion(R.id.gpt_work_gmail, R.string.gpt_work_gmail);
        bindGptWorkSuggestion(R.id.gpt_work_drive, R.string.gpt_work_drive);
        applyGptHomeTab(true);
    }

    private void bindGptWorkSuggestion(int viewId, final int textRes) {
        View row = homeView.findViewById(viewId);
        if (row != null) {
            row.setOnClickListener(v ->
                    startChatWith(getString(textRes), false));
        }
    }

    private void applyGptHomeTab(boolean chatTab) {
        if (!gptStyle) {
            return;
        }
        if (gptHomeNav != null) {
            gptHomeNav.setActiveTab(chatTab);
        }
        // 聊天页与工作页都不展示空态文案：聊天页仅留标题栏与输入框，
        // 工作页的任务建议入口在标题栏与输入框之间上下居中。
        if (gptEmptyWork != null) {
            if (!chatTab) {
                if (gptEmptyWork.getVisibility() != View.VISIBLE) {
                    gptEmptyWork.setVisibility(View.VISIBLE);
                    gptEmptyWork.scrollTo(0, 0);
                    animateGptWorkPanel(gptEmptyWork);
                }
            } else {
                gptEmptyWork.setVisibility(View.GONE);
            }
        }
        if (homeInput != null) {
            homeInput.setHint(chatTab ? R.string.gpt_hint_home : R.string.gpt_hint_work);
        }
    }

    /** 工作建议入口：0.34s 淡入并上移约 12dp，与参考稿 .panel-in 一致。 */
    private void animateGptWorkPanel(View panel) {
        panel.animate().cancel();
        panel.setAlpha(0f);
        panel.setTranslationY(dp(11.9f));
        panel.animate().alpha(1f).translationY(0f)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** 空对话标题栏的反馈条：屏幕下方 12vh 居中，1.8s 后淡出。 */
    private void showGptHomeToast(String message) {
        final TextView toast = findViewById(R.id.gpt_home_toast);
        if (toast == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        if (gptHomeToastHide != null) {
            handler.removeCallbacks(gptHomeToastHide);
        }
        ViewGroup.LayoutParams raw = toast.getLayoutParams();
        if (raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            lp.bottomMargin = Math.round(
                    getResources().getDisplayMetrics().heightPixels * 0.12f);
            toast.setLayoutParams(lp);
        }
        toast.setMaxWidth(Math.round(
                getResources().getDisplayMetrics().widthPixels * 0.82f));
        toast.animate().cancel();
        toast.setText(message);
        toast.setVisibility(View.VISIBLE);
        toast.setAlpha(0f);
        toast.setTranslationY(dp(4.4f));
        toast.animate().alpha(1f).translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        gptHomeToastHide = () -> {
            toast.animate().alpha(0f).translationY(dp(4.4f))
                    .setDuration(240)
                    .withEndAction(() -> toast.setVisibility(View.GONE))
                    .start();
        };
        handler.postDelayed(gptHomeToastHide, 1800);
    }

    // ---------------------------------------------------------------------
    // 起始页 Logo：长按从相册替换
    // ---------------------------------------------------------------------
    private File homeLogoFile() {
        return new File(getFilesDir(), "home_logo.png");
    }

    private void showHomeLogoOptions() {
        if (!homeLogoFile().exists()) {
            pickHomeLogo();
            return;
        }
        dialogBuilder()
                .setTitle(R.string.logo_sheet_title)
                .setItems(new String[]{getString(R.string.logo_change),
                        getString(R.string.logo_reset)}, (dialog, which) -> {
                    if (which == 0) {
                        pickHomeLogo();
                    } else {
                        resetHomeLogo();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickHomeLogo() {
        // 走系统图片选择器（相册），返回的图片会立刻复制到应用私有目录，
        // 因此不依赖相册的长期读取权限，也不需要申请存储权限。
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.logo_pick_title)), REQUEST_PICK_LOGO);
        } catch (Exception e) {
            Toast.makeText(this, R.string.logo_picker_missing, Toast.LENGTH_SHORT).show();
        }
    }

    /** 从相册读取照片，压到合适尺寸后写入应用私有目录，作为起始页 Logo 长期保存。 */
    private void handlePickedLogo(final Uri uri) {
        Toast.makeText(this, R.string.logo_loading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = decodePickedLogo(uri);
            final boolean saved = bitmap != null && saveHomeLogo(bitmap);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ImageView logo = homeView.findViewById(R.id.home_star);
                if (saved && logo != null) {
                    showCustomHomeLogo(logo, bitmap);
                    Toast.makeText(this, R.string.logo_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.logo_save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }, "home-logo-save").start();
    }

    private void applySavedHomeLogo(final ImageView logo) {
        final File file = homeLogoFile();
        if (!file.exists()) {
            showDefaultHomeLogo(logo);
            return;
        }
        new Thread(() -> {
            final Bitmap bitmap = decodeLogoFile(file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (bitmap == null) {
                    // 文件损坏时回退默认星芒，并清掉坏文件
                    file.delete();
                    showDefaultHomeLogo(logo);
                } else {
                    showCustomHomeLogo(logo, bitmap);
                }
            });
        }, "home-logo-load").start();
    }

    private void showDefaultHomeLogo(ImageView logo) {
        if (logo == null) {
            return;
        }
        logo.setImageResource(dsStyle ? R.drawable.ic_ds_whale : R.drawable.ic_starburst);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(null);
        logo.setClipToOutline(false);
    }

    private void showCustomHomeLogo(ImageView logo, Bitmap bitmap) {
        if (logo == null || bitmap == null) {
            return;
        }
        logo.setImageBitmap(bitmap);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackgroundResource(R.drawable.bg_logo_frame);
        // 借助圆形背景的 outline 把照片裁成圆形，和原 Logo 的视觉占位一致
        logo.setClipToOutline(true);
    }

    private void resetHomeLogo() {
        File file = homeLogoFile();
        if (file.exists()) {
            file.delete();
        }
        showDefaultHomeLogo(homeView.findViewById(R.id.home_star));
        Toast.makeText(this, R.string.logo_reset_done, Toast.LENGTH_SHORT).show();
    }

    private Bitmap decodePickedLogo(Uri uri) {
        return decodePickedImage(uri, HOME_LOGO_MAX_SIDE);
    }

    /** 先量尺寸再按采样率解码，避免把几千万像素的原图读进内存。 */
    private Bitmap decodePickedImage(Uri uri, int maxSide) {
        final int targetSide = Math.max(1, maxSide);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(input);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetSide
                && bounds.outHeight / (sample * 2) >= targetSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = null;
        input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input != null) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            // 读取失败时由调用方提示
        } finally {
            closeQuietly(input);
        }
        if (bitmap == null) {
            return null;
        }
        int rotation = readExifRotation(uri);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }
        return bitmap;
    }

    /** 读取相册照片的 EXIF 方向，避免竖拍照片显示成横向。 */
    private int readExifRotation(Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return 0;
        }
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(new BufferedInputStream(input));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        } finally {
            closeQuietly(input);
        }
    }

    private boolean saveHomeLogo(Bitmap bitmap) {
        Bitmap scaled = bitmap;
        int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxSide > HOME_LOGO_MAX_SIDE) {
            float ratio = HOME_LOGO_MAX_SIDE / (float) maxSide;
            int width = Math.max(1, Math.round(bitmap.getWidth() * ratio));
            int height = Math.max(1, Math.round(bitmap.getHeight() * ratio));
            scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        File target = homeLogoFile();
        File temp = new File(getFilesDir(), "home_logo.tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temp);
            boolean compressed = scaled.compress(Bitmap.CompressFormat.PNG, 100, output);
            output.flush();
            if (!compressed) {
                return false;
            }
            if (target.exists() && !target.delete()) {
                return false;
            }
            return temp.renameTo(target);
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(output);
            if (scaled != bitmap) {
                scaled.recycle();
            }
            if (temp.exists() && !temp.equals(target)) {
                temp.delete();
            }
        }
    }

    private Bitmap decodeLogoFile(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        int target = dp(46) * 2;
        while (bounds.outWidth / (sample * 2) >= target
                && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }

    private void refreshHomeApiHint() {
        if (homeView == null) {
            return;
        }
        View hint = homeView.findViewById(R.id.home_api_hint);
        if (hint != null) {
            // ChatGPT 参考图首页没有配置提示，避免破坏大留白的极简构图；
            // 未配置 Key 时首次发送仍会自动打开设置面板。
            hint.setVisibility(!gptStyle && !dsStyle
                    && prefs != null && !prefs.hasApiKey()
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void bindComposer(final EditText input, final ImageButton action, View attach,
                              final boolean incognito) {
        attachKeyboardOnTap(input);
        attach.setOnClickListener(v -> openSheet(SHEET_ATTACH));
        action.setOnClickListener(v -> {
            final String text = input.getText().toString().trim();
            if (text.length() > 0 || !pendingAttachments.isEmpty()) {
                if (hasLoadingAttachment()) {
                    Toast.makeText(this, R.string.attach_reading, Toast.LENGTH_SHORT).show();
                    return;
                }
                input.setText("");
                startChatWith(text, incognito);
            } else {
                hideKeyboard();
                showVoice();
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton(action, s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                final String text = input.getText().toString().trim();
                if (text.length() > 0 || !pendingAttachments.isEmpty()) {
                    if (hasLoadingAttachment()) {
                        Toast.makeText(this, R.string.attach_reading, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    input.setText("");
                    startChatWith(text, incognito);
                } else {
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });
    }

    // ---------------------------------------------------------------------
    // Incognito
    // ---------------------------------------------------------------------
    private void setupIncognito() {
        incognitoView.findViewById(R.id.incognito_menu).setOnClickListener(v -> openDrawer());
        incognitoView.findViewById(R.id.incognito_close).setOnClickListener(v -> showHome());
        TextView learn = incognitoView.findViewById(R.id.incognito_learn);
        learn.setPaintFlags(learn.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        learn.setOnClickListener(v ->
                Toast.makeText(this, R.string.incognito_desc, Toast.LENGTH_SHORT).show());

        incognitoModelView = incognitoView.findViewById(R.id.incognito_model);
        incognitoModelView.setOnClickListener(v -> openSheet(SHEET_FULL));

        incognitoInput = incognitoView.findViewById(R.id.incognito_input);
        ImageButton action = incognitoView.findViewById(R.id.incognito_send);
        incognitoComposerAction = action;
        bindComposer(incognitoInput, action,
                incognitoView.findViewById(R.id.incognito_attach), true);
        incognitoView.findViewById(R.id.incognito_mic).setOnClickListener(v -> {
            hideKeyboard();
            showVoice();
        });
    }

    // ---------------------------------------------------------------------
    // Voice / connection screen
    // ---------------------------------------------------------------------
    private void setupVoice() {
        voiceConnecting = voiceView.findViewById(R.id.voice_connecting);
        voiceFailed = voiceView.findViewById(R.id.voice_failed);
        voiceErrorCard = voiceView.findViewById(R.id.voice_error_card);

        voiceView.findViewById(R.id.voice_settings).setOnClickListener(v -> openSheet(SHEET_SETTINGS));
        View gptVoiceMenu = voiceView.findViewById(R.id.gpt_voice_menu);
        if (gptVoiceMenu != null) {
            gptVoiceMenu.setOnClickListener(v -> openDrawer());
        }
        voiceView.findViewById(R.id.voice_error_close).setOnClickListener(v ->
                voiceErrorCard.setVisibility(View.GONE));
        voiceView.findViewById(R.id.voice_retry).setOnClickListener(v -> retryConnection());
        voiceView.findViewById(R.id.voice_close).setOnClickListener(v -> closeVoice());
        voiceView.findViewById(R.id.voice_attach).setOnClickListener(v ->
                openSheet(SHEET_ATTACH));

        voiceModelView = voiceView.findViewById(R.id.voice_model);
        if (voiceModelView != null) {
            voiceModelView.setOnClickListener(v -> openSheet(SHEET_COMPACT));
        }

        View voiceMic = voiceView.findViewById(R.id.voice_mic);
        if (gptStyle && voiceMic instanceof SkyOrbView) {
            voiceOrbWrap = voiceView.findViewById(R.id.voice_orb_wrap);
            voiceOrb = (SkyOrbView) voiceMic;
            voiceOrbHalo = voiceView.findViewById(R.id.voice_orb_halo);
            voiceOrbFallback = voiceView.findViewById(R.id.voice_orb_fallback);
            voiceOrb.setOnReadyListener(ready -> {
                // 渲染成功时收起兜底图；失败则让静态渐变球透出，避免语音页只剩空白。
                if (voiceOrbFallback != null) {
                    voiceOrbFallback.setVisibility(ready ? View.GONE : View.VISIBLE);
                }
            });
            voiceOrb.setOnClickListener(v -> {
                if (voiceOrbWrap != null) {
                    voiceOrbWrap.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .withEndAction(() -> voiceOrbWrap.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(120).start())
                            .start();
                }
                voiceOrb.pulse();
            });
            voiceView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) ->
                    layoutVoiceOrb());
        } else if (voiceMic != null) {
            voiceMic.setOnClickListener(v ->
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(120).start())
                            .start());
        }

        View voiceSpeaker = voiceView.findViewById(R.id.voice_speaker);
        if (voiceSpeaker != null) {
            voiceSpeaker.setOnClickListener(v -> {
                audioMuted = !audioMuted;
                Toast.makeText(this, audioMuted ? "已静音" : "声音已开启", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 天空球尺寸按参考 HTML 的 --orb-size 换算：直径取 min(58.3333% 宽, 81.0811% 高)，
     * 光晕为球体的 132%。
     *
     * 位置由 view_voice_gpt.xml 的上下左右四向约束交给 ConstraintLayout 居中，
     * 这里只负责尺寸，并把历史遗留的顶距/底距清零，避免球被 margin 推离垂直中心。
     */
    private void layoutVoiceOrb() {
        if (voiceOrb == null || voiceOrbWrap == null || voiceView == null) {
            return;
        }
        int width = voiceView.getWidth();
        int height = voiceView.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int size = Math.round(Math.min(width * 0.583333f, height * 0.810811f));
        if (size <= 0) {
            return;
        }
        int haloSize = Math.round(size * 1.32f);

        ViewGroup.LayoutParams wrapParams = voiceOrbWrap.getLayoutParams();
        if (wrapParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) wrapParams;
            if (margins.width != size || margins.height != size
                    || margins.topMargin != 0 || margins.bottomMargin != 0) {
                margins.width = size;
                margins.height = size;
                margins.topMargin = 0;
                margins.bottomMargin = 0;
                voiceOrbWrap.setLayoutParams(margins);
            }
        }
        if (voiceOrbHalo != null) {
            ViewGroup.LayoutParams haloParams = voiceOrbHalo.getLayoutParams();
            if (haloParams.width != haloSize || haloParams.height != haloSize) {
                haloParams.width = haloSize;
                haloParams.height = haloSize;
                voiceOrbHalo.setLayoutParams(haloParams);
            }
        }
    }

    private void startVoiceOrb() {
        if (voiceOrb == null) {
            return;
        }
        voiceOrb.setLive(true);
        if (voiceOrbHalo != null) {
            voiceOrbHalo.show();
        }
        layoutVoiceOrb();
    }

    private void stopVoiceOrb() {
        if (voiceOrb == null) {
            return;
        }
        voiceOrb.setLive(false);
        if (voiceOrbHalo != null) {
            voiceOrbHalo.hide();
        }
    }

    private void retryConnection() {
        voiceErrorCard.setVisibility(View.VISIBLE);
        voiceFailed.setVisibility(View.GONE);
        voiceConnecting.setVisibility(View.VISIBLE);
        handler.removeCallbacks(connectionFailure);
        handler.postDelayed(connectionFailure, 2300);
    }

    // ---------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------
    private void setupChat() {
        chatScroll = chatView.findViewById(R.id.chat_scroll);
        chatMessages = chatView.findViewById(R.id.chat_messages);
        chatTitleView = chatView.findViewById(R.id.chat_title);
        chatInput = chatView.findViewById(R.id.chat_input);
        chatAction = chatView.findViewById(R.id.chat_action);
        chatModelView = chatView.findViewById(R.id.chat_model);

        View gptNavHeader = chatView.findViewById(R.id.gpt_chat_header);
        if (gptStyle && gptNavHeader instanceof GptChatNavBarView) {
            gptNavController = new GptChatNavController(this,
                    (GptChatNavBarView) gptNavHeader, new GptChatNavController.Listener() {
                        @Override
                        public void onOpenDrawer() {
                            // 与首页菜单完全一致：打开带搜索、导航和会话历史的侧边栏。
                            openDrawer();
                        }

                        @Override
                        public void onNewChatClick() {
                            // 会话页右上角铅笔是 ChatGPT 的「开启新对话」，不是编辑模式。
                            composeNewChat();
                        }

                        @Override
                        public void onSharePage() {
                            shareText(collectConversation());
                        }

                        @Override
                        public boolean onDeleteDraft() {
                            return deleteCurrentDraft();
                        }

                        @Override
                        public String pageLink() {
                            return currentConversationId > 0L
                                    ? "https://chatgpt.com/c/" + currentConversationId
                                    : "https://chatgpt.com/";
                        }
                    });
        } else {
            View chatMenu = chatView.findViewById(R.id.chat_menu);
            if (chatMenu != null) {
                chatMenu.setOnClickListener(v -> openDrawer());
            }
            View chatNew = chatView.findViewById(R.id.chat_new);
            if (chatNew != null) {
                chatNew.setOnClickListener(v -> newConversation());
            }
            View chatMore = chatView.findViewById(R.id.chat_more);
            if (chatMore != null) {
                chatMore.setOnClickListener(this::showChatMenu);
            }
        }
        chatView.findViewById(R.id.chat_attach).setOnClickListener(v -> openSheet(SHEET_ATTACH));
        chatView.findViewById(R.id.chat_mic).setOnClickListener(v -> {
            hideKeyboard();
            showVoice();
        });
        chatView.findViewById(R.id.chat_mic).setOnLongClickListener(v -> {
            showVoice();
            return true;
        });
        chatModelView.setOnClickListener(v -> openSheet(SHEET_FULL));
        chatAction.setOnClickListener(v -> onChatAction());
        attachKeyboardOnTap(chatInput);

        chatInput.addTextChangedListener(new TextWatcher() {            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateChatAction();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onChatAction();
                return true;
            }
            return false;
        });
        chatInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollChatToBottom();
            }
        });
        if (dsStyle) {
            View speaker = chatView.findViewById(R.id.chat_speaker);
            if (speaker != null) {
                speaker.setOnClickListener(v -> toggleSpeaker());
            }
            bindDsPill(chatView.findViewById(R.id.chat_think), true);
            bindDsPill(chatView.findViewById(R.id.chat_search), false);
            applyDsPills();
        }
        updateChatAction();
    }

    private void onChatAction() {
        final String text = chatInput.getText().toString().trim();
        final boolean hasAttachment = !pendingAttachments.isEmpty();
        if (chatSending) {
            // 生成中也允许继续输入：有草稿或附件时先停止当前回复再立即发送新消息，
            // 没有草稿时点击按钮只用于停止生成。
            stopChatGeneration();
            if (text.length() == 0 && !hasAttachment) {
                return;
            }
        }
        if (text.length() == 0 && !hasAttachment) {
            hideKeyboard();
            showVoice();
            return;
        }
        if (hasAttachment && hasLoadingAttachment()) {
            Toast.makeText(this, R.string.attach_reading, Toast.LENGTH_SHORT).show();
            return;
        }
        chatInput.setText("");
        // 清空内容后部分第三方输入法会丢失 input connection，
        // 主动重启一次连接，避免发送第二条消息后点输入框没有反应。
        restartInput(chatInput);
        dispatchChatMessage(text, takePendingAttachments());
    }

    private void startChatWith(String text, boolean incognito) {
        if (chatSending) {
            stopChatGeneration();
        }
        List<Attachment> attachments = takePendingAttachments();
        resetConversation();
        incognitoMode = incognito;
        showChatScreen();
        if (incognito) {
            chatTitleView.setText("无痕 · " + conversationTitle(text, attachments));
        }
        dispatchChatMessage(text, attachments);
    }

    private void openConversation(long conversationId) {
        ChatStore.Conversation conversation = store.getConversation(conversationId);
        if (conversation == null) {
            return;
        }
        if (chatSending) {
            stopChatGeneration();
        }
        resetConversation();
        incognitoMode = false;
        currentConversationId = conversationId;
        chatTitleView.setText(conversation.title);
        List<ChatStore.Message> messages = store.loadMessages(conversationId);
        for (ChatStore.Message message : messages) {
            if (ChatStore.ROLE_USER.equals(message.role)) {
                List<Attachment> attachments = decodeAttachments(message.content);
                String visible = decodeVisibleText(message.content);
                history.add(new Turn(message.role, visible, message.id, attachments));
            } else {
                history.add(new Turn(message.role, message.content, message.id));
            }
        }
        showChatScreen();
        renderHistory();
        scrollChatToBottom();
    }

    private void renderHistory() {
        chatMessages.removeAllViews();
        removeLoadingRow();
        removeErrorRow();
        for (int i = 0; i < history.size(); i++) {
            Turn turn = history.get(i);
            View row;
            if (ChatStore.ROLE_USER.equals(turn.role)) {
                row = addUserBubble(turn.text, turn.attachments);
            } else {
                row = addAiMessage(turn.text, i);
            }
            row.setTag(i);
        }
    }

    private void newConversation() {
        stopChatGeneration();
        resetConversation();
        showHome();
        Toast.makeText(this, R.string.new_conversation, Toast.LENGTH_SHORT).show();
    }

    /**
     * 会话页顶部铅笔按钮：开启一段新对话。
     *
     * <p>当前会话已经写入会话库，直接清空当前上下文并回到首页的新对话态，
     * 把焦点交给输入框，方便立刻输入下一条消息；这里不再切换「编辑模式」。</p>
     */
    private void composeNewChat() {
        stopChatGeneration();
        resetConversation();
        showHome();
        if (homeInput != null) {
            homeInput.requestFocus();
        }
    }

    private void resetConversation() {
        handler.removeCallbacks(streamFlush);
        if (client != null) {
            client.cancel();
        }
        stopSpeaking();
        removeLoadingRow();
        removeErrorRow();
        chatSending = false;
        aiRow = null;
        aiBody = null;
        aiActions = null;
        aiDisclaimer = null;
        aiTurnIndex = -1;
        streamBuffer.setLength(0);
        streamingText = "";
        flushScheduled = false;
        modelSwitchedNotified = false;
        history.clear();
        currentConversationId = 0L;
        incognitoMode = false;
        chatMessages.removeAllViews();
        pendingAttachments.clear();
        refreshAttachmentStrips();
        clearComposer();
    }

    private void dispatchChatMessage(String text, List<Attachment> attachments) {
        // 发送即收起输入法面板，把刚发出的消息和随后的回复完整让出来。
        dismissComposerKeyboard();
        removeErrorRow();
        final String visible = text == null ? "" : text;
        final List<Attachment> atts = attachments == null
                ? new ArrayList<>() : new ArrayList<>(attachments);
        final String title = conversationTitle(visible, atts);
        long dbId = 0L;
        if (!incognitoMode) {
            if (currentConversationId == 0L) {
                currentConversationId = store.createConversation(title);
            }
            dbId = store.addMessage(currentConversationId, ChatStore.ROLE_USER,
                    encodeUserMessage(visible, atts));
        }
        history.add(new Turn(ChatStore.ROLE_USER, visible, dbId, atts));

        View row = addUserBubble(visible, atts);
        row.setTag(history.size() - 1);

        if (incognitoMode) {
            chatTitleView.setText("无痕 · " + title);
        } else if (currentConversationId > 0L) {
            ChatStore.Conversation conversation = store.getConversation(currentConversationId);
            if (conversation != null) {
                chatTitleView.setText(conversation.title);
            }
        }
        scrollChatToBottom();
        beginStreaming();
    }

    private void beginStreaming() {
        if (prefs == null || !prefs.hasApiKey()) {
            showErrorRow(getString(R.string.api_required_title),
                    getString(R.string.api_required_desc));
            restoreComposer();
            scrollChatToBottom();
            openSheet(SHEET_SETTINGS);
            return;
        }
        if (history.isEmpty() || !ChatStore.ROLE_USER.equals(history.get(history.size() - 1).role)) {
            return;
        }
        chatSending = true;
        setComposerSendingState();
        // 发送后收起输入法面板；输入框仍然可用，点一下即可继续输入下一条。
        dismissComposerKeyboard();
        streamingText = "";
        streamBuffer.setLength(0);
        flushScheduled = false;
        aiTurnIndex = history.size();

        aiRow = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_msg_ai,
                R.layout.item_msg_ai_gpt, R.layout.item_msg_ai_ds), chatMessages, false);
        aiBody = aiRow.findViewById(R.id.msg_ai_text);
        aiActions = aiRow.findViewById(R.id.msg_ai_actions);
        aiDisclaimer = aiRow.findViewById(R.id.msg_ai_disclaimer);
        // DeepSeek 的 HTML 图标行需要保持测量（WebView 依赖真实宽度计算 viewport），
        // 因此用 INVISIBLE 而非 GONE；其余风格继续 GONE，不额外占位。
        aiActions.setVisibility(aiActions instanceof DsActionsView
                ? View.INVISIBLE : View.GONE);
        aiDisclaimer.setVisibility(View.GONE);
        if (gptStyle || dsStyle) {
            applyAiRowStyle(aiRow);
        }
        bindAiActions(aiRow, aiBody);
        chatMessages.addView(aiRow);
        aiRow.setTag(aiTurnIndex);
        fadeInRow(aiRow, 12);
        showLoadingDot();
        scrollChatToBottom();
        requestReply();
    }

    private void requestReply() {
        List<DeepSeekClient.Msg> messages = new ArrayList<>();
        String system = prefs.systemPrompt();
        if (dsStyle || gptStyle) {
            // 深度思考是真实生效的开关：直接追加到本次请求的系统提示中。
            if (thinkOn) {
                system = system + "\n\n请先进行深入的逐步推理，再给出结论；最终回答保持简洁清晰。";
            }
            if (dsStyle && webSearchOn) {
                system = system + "\n\n用户已开启智能搜索：请结合你掌握的最新公开信息回答，"
                        + "无法确认时明确说明。";
            }
        }
        messages.add(new DeepSeekClient.Msg("system", system));
        replyStartedAt = System.currentTimeMillis();
        int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        List<Turn> turns = new ArrayList<>(history.subList(start, history.size()));
        // 只有选中的图片才会真正编码成 base64 随请求发送，避免历史图片把请求体撑爆
        java.util.Set<Attachment> sendableImages = selectImagesForApi(turns);
        for (Turn turn : turns) {
            if (turn.text.trim().isEmpty() && turn.attachments.isEmpty()) {
                continue;
            }
            boolean isUser = ChatStore.ROLE_USER.equals(turn.role);
            String content;
            List<String> images = new ArrayList<>();
            if (isUser) {
                // 先真正编码图片，再按编码结果生成提示文字，保证“已附带”与实际情况一致
                java.util.Set<Attachment> attached = java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<Attachment, Boolean>());
                for (Attachment attachment : turn.attachments) {
                    if (!sendableImages.contains(attachment)) {
                        continue;
                    }
                    String dataUrl = imageDataUrl(attachment);
                    if (dataUrl != null) {
                        images.add(dataUrl);
                        attached.add(attachment);
                    }
                }
                content = buildApiContent(turn.text, turn.attachments, attached);
            } else {
                content = turn.text;
            }
            if (content.length() > MAX_TURN_CHARS) {
                content = content.substring(0, MAX_TURN_CHARS);
            }
            messages.add(new DeepSeekClient.Msg(turn.role, content, images));
        }

        modelSwitchedNotified = false;
        imagesDroppedNotified = false;
        final String model = prefs.model();
        client.stream(prefs.apiKey(), prefs.baseUrl(), model, Prefs.FALLBACK_MODEL,
                messages, new DeepSeekClient.Callback() {
                    @Override
                    public void onDelta(final String delta) {
                        handler.post(() -> handleDelta(delta));
                    }

                    @Override
                    public void onDone(final String full) {
                        handler.post(() -> {
                            if (!chatSending) {
                                return;
                            }
                            handler.removeCallbacks(streamFlush);
                            String text = full == null ? "" : full;
                            if (text.trim().isEmpty()) {
                                text = streamBuffer.toString();
                            }
                            completeReply(text);
                        });
                    }

                    @Override
                    public void onError(final String message, final int code) {
                        handler.post(() -> {
                            if (!chatSending) {
                                return;
                            }
                            handler.removeCallbacks(streamFlush);
                            failReply(message);
                        });
                    }

                    @Override
                    public void onModelSwitched(final String switchedModel) {
                        handler.post(() -> {
                            if (modelSwitchedNotified) {
                                return;
                            }
                            modelSwitchedNotified = true;
                            // 记住实际可用的模型，后续对话不再重复失败一次
                            prefs.setModel(switchedModel);
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.api_switch_model, switchedModel),
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onImagesDropped() {
                        handler.post(() -> {
                            if (imagesDroppedNotified) {
                                return;
                            }
                            imagesDroppedNotified = true;
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.attach_images_dropped),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void handleDelta(String delta) {
        if (!chatSending || delta == null || delta.isEmpty()) {
            return;
        }
        removeLoadingRow();
        streamBuffer.append(delta);
        if (!flushScheduled) {
            flushScheduled = true;
            handler.postDelayed(streamFlush, 50);
        }
    }

    private void completeReply(String text) {
        handler.removeCallbacks(streamFlush);
        flushScheduled = false;
        removeLoadingRow();
        if (text == null || text.trim().isEmpty()) {
            failReply("模型没有返回任何内容，请稍后重试。");
            return;
        }
        streamingText = text;
        if (aiBody != null) {
            aiBody.setText(text);
        }
        if (aiActions != null) {
            aiActions.setVisibility(View.VISIBLE);
        }
        if (dsStyle && thinkOn && aiRow != null) {
            TextView thought = aiRow.findViewById(R.id.msg_ai_thought);
            if (thought != null) {
                long seconds = Math.max(1L,
                        (System.currentTimeMillis() - replyStartedAt) / 1000L);
                thought.setText(getString(R.string.ds_thought, seconds));
                thought.setVisibility(View.VISIBLE);
            }
        }
        if (aiDisclaimer != null) {
            aiDisclaimer.setVisibility(gptStyle || dsStyle ? View.GONE : View.VISIBLE);
        }
        long dbId = 0L;
        if (!incognitoMode && currentConversationId > 0L) {
            dbId = store.addMessage(currentConversationId, ChatStore.ROLE_ASSISTANT, text);
        }
        history.add(new Turn(ChatStore.ROLE_ASSISTANT, text, dbId));

        chatSending = false;
        aiRow = null;
        aiBody = null;
        aiActions = null;
        aiDisclaimer = null;
        aiTurnIndex = -1;
        streamBuffer.setLength(0);
        streamingText = "";
        restoreComposer();
        scrollChatToBottom();
    }

    private void failReply(String message) {
        handler.removeCallbacks(streamFlush);
        flushScheduled = false;
        removeLoadingRow();
        if (streamBuffer.length() > 0) {
            streamingText = streamBuffer.toString();
        }
        if (aiBody != null && !streamingText.trim().isEmpty()) {
            aiBody.setText(streamingText);
            if (aiActions != null) {
                aiActions.setVisibility(View.VISIBLE);
            }
            long dbId = 0L;
            if (!incognitoMode && currentConversationId > 0L) {
                dbId = store.addMessage(currentConversationId, ChatStore.ROLE_ASSISTANT,
                        streamingText);
            }
            history.add(new Turn(ChatStore.ROLE_ASSISTANT, streamingText, dbId));
        } else if (aiRow != null && aiRow.getParent() == chatMessages) {
            chatMessages.removeView(aiRow);
        }
        aiRow = null;
        aiBody = null;
        aiActions = null;
        aiDisclaimer = null;
        aiTurnIndex = -1;
        streamBuffer.setLength(0);
        streamingText = "";
        showErrorRow(getString(R.string.error_title),
                (message == null ? "请求失败" : message)
                        + "\n请检查 API Key、接口地址或网络后重试。");
        chatSending = false;
        restoreComposer();
        scrollChatToBottom();
    }

    private void showErrorRow(String title, String description) {
        removeErrorRow();
        View row = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_msg_error,
                R.layout.item_msg_error_gpt, R.layout.item_msg_error_ds),
                chatMessages, false);
        ((TextView) row.findViewById(R.id.msg_error_title)).setText(title);
        ((TextView) row.findViewById(R.id.msg_error_desc)).setText(description);
        row.findViewById(R.id.msg_error_retry).setOnClickListener(v -> {
            removeErrorRow();
            retryLast();
        });
        chatMessages.addView(row);
        errorRow = row;
        fadeInRow(row, 10);
    }

    private void removeErrorRow() {
        if (errorRow != null && errorRow.getParent() == chatMessages) {
            chatMessages.removeView(errorRow);
        }
        errorRow = null;
    }

    private void retryLast() {
        if (chatSending) {
            return;
        }
        if (!history.isEmpty()
                && ChatStore.ROLE_ASSISTANT.equals(history.get(history.size() - 1).role)) {
            Turn last = history.remove(history.size() - 1);
            if (!incognitoMode && last.dbId > 0L) {
                store.deleteMessage(last.dbId);
            }
            int rowIndex = history.size();
            for (int i = chatMessages.getChildCount() - 1; i >= 0; i--) {
                View child = chatMessages.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer && ((Integer) tag) == rowIndex) {
                    chatMessages.removeViewAt(i);
                    break;
                }
            }
        }
        if (history.isEmpty()
                || !ChatStore.ROLE_USER.equals(history.get(history.size() - 1).role)) {
            openSheet(SHEET_SETTINGS);
            return;
        }
        beginStreaming();
    }

    private void setComposerSendingState() {
        // 这里只刷新输入框与发送按钮的状态，不再禁用输入框、不再覆盖“正在发送...”占位文案；
        // 输入法面板会在发送后统一收起（见 dismissComposerKeyboard 的调用点）。
        chatInput.setEnabled(true);
        chatInput.setTextColor(ContextCompat.getColor(this, composerTextColor()));
        chatInput.setHint(hintReplyRes());
        updateChatAction();
    }

    private void restoreComposer() {
        chatInput.setEnabled(true);
        chatInput.setTextColor(ContextCompat.getColor(this, composerTextColor()));
        chatInput.setHint(hintReplyRes());
        // 保留生成期间用户已经输入的草稿，不能在这里无条件清空
        updateChatAction();
    }

    /** 重置会话时才清空输入框。 */
    private void clearComposer() {
        chatInput.setText("");
        chatInput.setEnabled(true);
        chatInput.setTextColor(ContextCompat.getColor(this, composerTextColor()));
        chatInput.setHint(hintReplyRes());
        updateChatAction();
    }

    private int hintReplyRes() {
        if (dsStyle) {
            return R.string.ds_hint_reply;
        }
        return gptStyle ? R.string.gpt_hint_reply : R.string.hint_reply;
    }

    private int composerTextColor() {
        if (dsStyle) {
            return R.color.ds_text;
        }
        return gptStyle ? R.color.gpt_text : R.color.text_primary;
    }

    private void updateChatAction() {
        boolean hasText = chatInput.getText().length() > 0 || !pendingAttachments.isEmpty();
        if (dsStyle) {
            // DeepSeek 风格：有内容时是品牌蓝圆形向上箭头，空白时是灰色语音按钮。
            chatAction.clearColorFilter();
            if (hasText) {
                chatAction.setBackgroundResource(R.drawable.bg_ds_composer_send);
                chatAction.setImageResource(R.drawable.ic_ds_arrow_up);
                chatAction.setContentDescription(getString(R.string.cd_send));
            } else if (chatSending) {
                chatAction.setBackgroundResource(R.drawable.bg_ds_composer_send);
                chatAction.setImageResource(R.drawable.ic_ds_stop);
                chatAction.setColorFilter(Color.WHITE);
                chatAction.setContentDescription(getString(R.string.cd_stop_generating));
            } else {
                chatAction.setBackgroundResource(R.drawable.bg_ds_composer_voice);
                chatAction.setImageResource(R.drawable.ic_ds_composer_voice);
                chatAction.setContentDescription(getString(R.string.cd_voice));
            }
            return;
        }
        if (gptStyle) {
            // ChatGPT 风格：蓝色圆形按钮，空白时是语音波形，有内容时是向上箭头。
            chatAction.setBackgroundResource(R.drawable.bg_gpt_blue_circle);
            if (hasText) {
                chatAction.setImageResource(R.drawable.ic_gpt_arrow_up);
                chatAction.setContentDescription(getString(R.string.cd_send));
            } else if (chatSending) {
                chatAction.setImageResource(R.drawable.ic_gpt_stop_white);
                chatAction.setContentDescription(getString(R.string.cd_stop_generating));
            } else {
                chatAction.setImageResource(R.drawable.ic_gpt_waveform);
                chatAction.setContentDescription(getString(R.string.cd_voice));
            }
            return;
        }
        if (hasText) {
            // 生成过程中只要用户输入了内容，按钮就恢复成发送；
            // 点击时会先停止当前生成，再发送这条新消息。
            chatAction.setBackgroundResource(R.drawable.bg_send_orange);
            chatAction.setImageResource(R.drawable.ic_send_white);
            chatAction.setContentDescription(getString(R.string.cd_send));
        } else if (chatSending) {
            chatAction.setBackgroundResource(R.drawable.bg_send_muted);
            chatAction.setImageResource(R.drawable.ic_stop_square);
            chatAction.setContentDescription(getString(R.string.cd_stop_generating));
        } else {
            chatAction.setBackgroundResource(R.drawable.bg_send_idle);
            chatAction.setImageResource(R.drawable.ic_waveform);
            chatAction.setContentDescription(getString(R.string.cd_voice));
        }
    }

    private void stopChatGeneration() {
        if (!chatSending) {
            return;
        }
        chatSending = false;
        if (client != null) {
            client.cancel();
        }
        handler.removeCallbacks(streamFlush);
        flushScheduled = false;
        removeLoadingRow();
        if (streamBuffer.length() > 0) {
            streamingText = streamBuffer.toString();
        }
        if (aiBody != null && !streamingText.trim().isEmpty()) {
            aiBody.setText(streamingText);
            if (aiActions != null) {
                aiActions.setVisibility(View.VISIBLE);
            }
            if (aiDisclaimer != null) {
                aiDisclaimer.setVisibility(gptStyle || dsStyle ? View.GONE : View.VISIBLE);
            }
            long dbId = 0L;
            if (!incognitoMode && currentConversationId > 0L) {
                dbId = store.addMessage(currentConversationId, ChatStore.ROLE_ASSISTANT,
                        streamingText);
            }
            history.add(new Turn(ChatStore.ROLE_ASSISTANT, streamingText, dbId));
        } else if (aiRow != null && aiRow.getParent() == chatMessages) {
            chatMessages.removeView(aiRow);
        }
        aiRow = null;
        aiBody = null;
        aiActions = null;
        aiDisclaimer = null;
        aiTurnIndex = -1;
        streamBuffer.setLength(0);
        streamingText = "";
        restoreComposer();
        scrollChatToBottom();
    }

    private void showLoadingDot() {
        removeLoadingRow();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(14), 0, dp(2));
        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.bg_loading_dot);
        wrap.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        chatMessages.addView(wrap);
        loadingRow = wrap;
        dot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_dot));
    }

    private void removeLoadingRow() {
        if (loadingRow != null && loadingRow.getParent() == chatMessages) {
            chatMessages.removeView(loadingRow);
        }
        loadingRow = null;
    }

    private View addUserBubble(String text, List<Attachment> attachments) {
        View row = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_msg_user,
                R.layout.item_msg_user_gpt, R.layout.item_msg_user_ds),
                chatMessages, false);
        TextView body = row.findViewById(R.id.msg_user_text);
        View strip = row.findViewById(R.id.msg_user_attach_scroll);
        LinearLayout holder = row.findViewById(R.id.msg_user_attachments);
        if (attachments != null && !attachments.isEmpty()) {
            strip.setVisibility(View.VISIBLE);
            for (Attachment attachment : attachments) {
                holder.addView(buildAttachmentCard(attachment, false));
            }
        } else {
            strip.setVisibility(View.GONE);
        }
        if (text == null || text.trim().isEmpty()) {
            body.setVisibility(View.GONE);
        } else {
            body.setVisibility(View.VISIBLE);
            body.setText(text);
            body.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.76f));
            body.setOnLongClickListener(v -> {
                copyToClipboard(body.getText().toString());
                return true;
            });
        }
        chatMessages.addView(row);
        fadeInRow(row, 12);
        return row;
    }

    private View addAiMessage(String reply, int historyIndex) {
        View row = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_msg_ai,
                R.layout.item_msg_ai_gpt, R.layout.item_msg_ai_ds), chatMessages, false);
        TextView body = row.findViewById(R.id.msg_ai_text);
        body.setText(reply);
        row.findViewById(R.id.msg_ai_actions).setVisibility(View.VISIBLE);
        row.findViewById(R.id.msg_ai_disclaimer).setVisibility(gptStyle || dsStyle
                ? View.GONE : View.VISIBLE);
        if (gptStyle || dsStyle) {
            applyAiRowStyle(row);
        }
        bindAiActions(row, body);
        row.setTag(historyIndex);
        chatMessages.addView(row);
        fadeInRow(row, 12);
        return row;
    }

    /** ChatGPT / DeepSeek 风格的消息操作行使用中性灰图标，并隐藏底部的 Claude 免责声明。 */
    private void applyAiRowStyle(View row) {
        int[] ids = {R.id.msg_action_copy, R.id.msg_action_like, R.id.msg_action_dislike,
                R.id.msg_action_play, R.id.msg_action_share, R.id.msg_action_regen};
        int color = ContextCompat.getColor(this, dsStyle ? R.color.ds_action_icon
                : (gptStyle ? R.color.gpt_text_secondary : R.color.action_icon));
        for (int id : ids) {
            ImageButton button = row.findViewById(id);
            if (button != null) {
                button.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
        View disclaimer = row.findViewById(R.id.msg_ai_disclaimer);
        if (disclaimer != null) {
            disclaimer.setVisibility(View.GONE);
        }
    }

    private void tintMessageAction(ImageButton button) {
        if (button == null) {
            return;
        }
        button.clearColorFilter();
        if (gptStyle || dsStyle) {
            button.setColorFilter(ContextCompat.getColor(this,
                            dsStyle ? R.color.ds_action_icon : R.color.gpt_text_secondary),
                    PorterDuff.Mode.SRC_IN);
        }
    }

    private void bindAiActions(final View row, final TextView body) {
        View actionsContainer = row.findViewById(R.id.msg_ai_actions);
        if (actionsContainer instanceof DsActionsView) {
            bindDsActions(row, (DsActionsView) actionsContainer, body);
            return;
        }
        final ImageButton copy = row.findViewById(R.id.msg_action_copy);
        final ImageButton share = row.findViewById(R.id.msg_action_share);
        final ImageButton play = row.findViewById(R.id.msg_action_play);
        final ImageButton like = row.findViewById(R.id.msg_action_like);
        final ImageButton dislike = row.findViewById(R.id.msg_action_dislike);
        final ImageButton regen = row.findViewById(R.id.msg_action_regen);

        copy.setOnClickListener(v -> copyToClipboard(body.getText().toString()));
        share.setOnClickListener(v -> shareText(body.getText().toString()));
        if (play != null) {
            play.setOnClickListener(v -> toggleSpeak(play, body.getText().toString()));
        }
        like.setOnClickListener(v -> setReaction(like, dislike, true));
        dislike.setOnClickListener(v -> setReaction(like, dislike, false));
        if (gptStyle) {
            // ChatGPT 参考图最后一个操作是“更多”三点；该按钮由 regen 位承载。
            regen.setOnClickListener(v -> showMessageMenu(row, body));
        } else {
            regen.setOnClickListener(v -> regenerateReply(row));
        }
    }

    /**
     * DeepSeek / ChatGPT：操作行分别由 assets/ds_actions.html、gpt_actions.html
     * 在 WebView 中绘制，点击事件经 JS 桥回调后仍复用原生的复制 / 分享 / 朗读 /
     * 反馈 / 重新生成 / 更多菜单逻辑。
     */
    private void bindDsActions(final View row, final DsActionsView actions, final TextView body) {
        actions.setOnActionListener(action -> {
            if (action == null) {
                return;
            }
            switch (action) {
                case "copy":
                    copyToClipboard(body.getText().toString());
                    break;
                case "share":
                    shareText(body.getText().toString());
                    break;
                case "play":
                    toggleSpeak(actions, body.getText().toString());
                    break;
                case "like":
                    toggleDsReaction(actions, true);
                    break;
                case "dislike":
                    toggleDsReaction(actions, false);
                    break;
                case "regen":
                    regenerateReply(row);
                    break;
                case "more":
                    // ChatGPT 操作行末位的三点仍打开原有的消息菜单。
                    showMessageMenu(row, body);
                    break;
                default:
                    break;
            }
        });
    }

    /** 0 = 无，1 = 点赞，2 = 点踩；再次点击已选中的图标即取消。 */
    private void toggleDsReaction(DsActionsView actions, boolean isLike) {
        final int target = isLike ? 1 : 2;
        final int next = actions.getReaction() == target ? 0 : target;
        actions.setReaction(next);
        if (next != 0) {
            Toast.makeText(this, R.string.liked, Toast.LENGTH_SHORT).show();
        }
    }

    private AlertDialog.Builder dialogBuilder() {
        // 三套皮肤使用各自的强调色，避免 DeepSeek / ChatGPT 页面里弹出橙色 Claude 按钮。
        if (dsStyle) {
            return new AlertDialog.Builder(this, R.style.Theme_App_DsDialog);
        }
        return gptStyle
                ? new AlertDialog.Builder(this, R.style.Theme_App_GptDialog)
                : new AlertDialog.Builder(this);
    }

    private void showMessageMenu(final View row, final TextView body) {
        AlertDialog.Builder builder = dialogBuilder();
        builder.setItems(new String[]{getString(R.string.cd_regenerate),
                        getString(R.string.cd_copy), getString(R.string.cd_share)},
                        (dialog, which) -> {
                            if (which == 0) {
                                regenerateReply(row);
                            } else if (which == 1) {
                                copyToClipboard(body.getText().toString());
                            } else {
                                shareText(body.getText().toString());
                            }
                        })
                .show();
    }

    private void setReaction(ImageButton like, ImageButton dislike, boolean isLike) {
        final ImageButton target = isLike ? like : dislike;
        final ImageButton other = isLike ? dislike : like;
        final boolean active = Boolean.TRUE.equals(target.getTag());
        other.setTag(null);
        tintMessageAction(other);
        if (active) {
            target.setTag(null);
            tintMessageAction(target);
        } else {
            target.setTag(Boolean.TRUE);
            target.setColorFilter(ContextCompat.getColor(this, dsStyle
                    ? R.color.ds_brand
                    : (gptStyle ? R.color.gpt_blue : R.color.orange)));
            Toast.makeText(this, R.string.liked, Toast.LENGTH_SHORT).show();
        }
    }

    private void regenerateReply(View row) {
        if (chatSending) {
            return;
        }
        Object tag = row.getTag();
        if (!(tag instanceof Integer)) {
            return;
        }
        int index = (Integer) tag;
        if (index < 0 || index >= history.size()) {
            return;
        }
        int childIndex = chatMessages.indexOfChild(row);
        if (childIndex < 0) {
            return;
        }
        removeLoadingRow();
        removeErrorRow();
        while (chatMessages.getChildCount() > childIndex) {
            chatMessages.removeViewAt(chatMessages.getChildCount() - 1);
        }
        Turn target = history.get(index);
        if (!incognitoMode && currentConversationId > 0L && target.dbId > 0L) {
            store.deleteMessagesFrom(currentConversationId, target.dbId);
        }
        while (history.size() > index) {
            history.remove(history.size() - 1);
        }
        if (history.isEmpty()
                || !ChatStore.ROLE_USER.equals(history.get(history.size() - 1).role)) {
            return;
        }
        stopSpeaking();
        scrollChatToBottom();
        beginStreaming();
    }

    private void copyToClipboard(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return;
        }
        String label = gptStyle ? getString(R.string.gpt_app_name)
                : (dsStyle ? "DeepSeek" : getString(R.string.app_name));
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.cd_share)));
        } catch (Exception ignored) {
            // No share target installed.
        }
    }

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) {
                return;
            }
            int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(Locale.CHINA);
            }
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(MainActivity.this::resetSpeakButton);
                }

                @Override
                public void onError(String utteranceId) {
                    runOnUiThread(MainActivity.this::resetSpeakButton);
                }
            });
        });
    }

    private void toggleSpeak(final ImageButton button, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (tts == null || !ttsReady) {
            Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (speakingButton == button) {
            tts.stop();
            resetSpeakButton();
            return;
        }
        resetSpeakButton();
        speakingButton = button;
        button.setImageResource(R.drawable.ic_play_stop);
        button.setContentDescription(getString(R.string.cd_stop));
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "claude_" + System.currentTimeMillis());
    }

    /** DeepSeek 的朗读入口在 WebView 图标行内，播放态通过 JS 同步给 HTML。 */
    private void toggleSpeak(final DsActionsView actions, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (tts == null || !ttsReady) {
            Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (speakingActions == actions) {
            tts.stop();
            resetSpeakButton();
            return;
        }
        resetSpeakButton();
        speakingActions = actions;
        actions.setPlaying(true);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "claude_" + System.currentTimeMillis());
    }

    private void stopSpeaking() {
        if (tts != null && ttsReady) {
            tts.stop();
        }
        resetSpeakButton();
    }

    private void resetSpeakButton() {
        if (speakingButton != null) {
            speakingButton.setImageResource(dsStyle
                    ? R.drawable.ic_ds_action_volume : R.drawable.ic_play);
            speakingButton.setContentDescription(getString(R.string.cd_play));
            speakingButton = null;
        }
        if (speakingActions != null) {
            speakingActions.setPlaying(false);
            speakingActions = null;
        }
    }

    private void showChatMenu(View anchor) {
        if (chatMenuPopup != null && chatMenuPopup.isShowing()) {
            chatMenuPopup.dismiss();
            return;
        }
        final View menu = LayoutInflater.from(this).inflate(styleLayout(
                R.layout.popup_chat_menu, R.layout.popup_chat_menu_gpt,
                R.layout.popup_chat_menu_ds), null);
        chatMenuPopup = new PopupWindow(menu, dp(196),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        chatMenuPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        chatMenuPopup.setOutsideTouchable(true);
        chatMenuPopup.setElevation(dp(10));
        chatMenuPopup.setOnDismissListener(() -> chatMenuPopup = null);

        menu.findViewById(R.id.menu_rename).setOnClickListener(v -> {
            dismissChatMenu();
            showRenameDialog();
        });
        menu.findViewById(R.id.menu_star).setOnClickListener(v -> {
            dismissChatMenu();
            toggleStar();
        });
        menu.findViewById(R.id.menu_share).setOnClickListener(v -> {
            dismissChatMenu();
            shareText(collectConversation());
        });
        menu.findViewById(R.id.menu_delete).setOnClickListener(v -> {
            dismissChatMenu();
            deleteConversation();
        });

        chatMenuPopup.showAsDropDown(anchor, dp(44) - dp(196) - dp(4), dp(6));
        menu.setAlpha(0f);
        menu.setScaleX(0.96f);
        menu.setScaleY(0.96f);
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void dismissChatMenu() {
        if (chatMenuPopup != null) {
            chatMenuPopup.dismiss();
            chatMenuPopup = null;
        }
    }

    private void toggleStar() {
        if (incognitoMode || currentConversationId == 0L) {
            Toast.makeText(this, R.string.incognito_unsaved, Toast.LENGTH_SHORT).show();
            return;
        }
        ChatStore.Conversation conversation = store.getConversation(currentConversationId);
        if (conversation == null) {
            return;
        }
        store.setStarred(currentConversationId, !conversation.starred);
        Toast.makeText(this, R.string.starred, Toast.LENGTH_SHORT).show();
    }

    private void showRenameDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(chatTitleView.getText());
        input.setSelection(input.getText().length());
        input.setTextSize(16);
        input.setTextColor(ContextCompat.getColor(this, dsStyle ? R.color.ds_text
                : (gptStyle ? R.color.gpt_text : R.color.text_primary)));
        input.setHint(R.string.rename_hint);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = dp(20);
        wrapper.setPadding(pad, dp(4), pad, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = dialogBuilder()
                .setTitle(R.string.chat_rename)
                .setView(wrapper)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogView, which) -> {
                    String title = input.getText().toString().trim();
                    if (title.length() > 0) {
                        if (incognitoMode) {
                            chatTitleView.setText(title);
                        } else if (currentConversationId > 0L) {
                            store.renameConversation(currentConversationId, title);
                            chatTitleView.setText(title);
                            buildRecentList();
                        }
                        Toast.makeText(this, R.string.renamed, Toast.LENGTH_SHORT).show();
                    }
                })
                .create();
        dialog.show();
        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void deleteConversation() {
        deleteConversation(true);
    }

    private void deleteConversation(boolean notifyUser) {
        stopChatGeneration();
        if (!incognitoMode && currentConversationId > 0L) {
            store.deleteConversation(currentConversationId);
        }
        resetConversation();
        buildRecentList();
        showHome();
        if (notifyUser) {
            Toast.makeText(this, R.string.conversation_deleted, Toast.LENGTH_SHORT).show();
        }
    }

    /** 标题栏「删除草稿」：只有确实存在已保存会话时才执行删除。 */
    private boolean deleteCurrentDraft() {
        boolean hadDraft = !incognitoMode && currentConversationId > 0L;
        if (hadDraft) {
            deleteConversation(false);
        }
        return hadDraft;
    }

    private String collectConversation() {
        StringBuilder builder = new StringBuilder();
        builder.append(chatTitleView.getText()).append("\n\n");
        for (Turn turn : history) {
            String text = turn.text;
            if (text.trim().isEmpty() && !turn.attachments.isEmpty()) {
                StringBuilder names = new StringBuilder("[附件] ");
                for (Attachment attachment : turn.attachments) {
                    names.append(attachment.name).append(' ');
                }
                text = names.toString().trim();
            }
            builder.append(ChatStore.ROLE_USER.equals(turn.role)
                            ? "我：" : getString(R.string.app_name) + "：")
                    .append(text).append("\n\n");
        }
        return builder.toString().trim();
    }

    private void scrollChatToBottom() {
        if (chatScroll == null || chatMessages == null) {
            return;
        }
        chatScroll.post(() -> {
            if (chatScroll == null || chatMessages == null) {
                return;
            }
            // 这里不再使用 fullScroll(FOCUS_DOWN)：它会参与焦点计算，
            // 在消息变多、流式输出频繁滚动时可能把焦点从输入框抢走。
            int target = chatMessages.getHeight() - chatScroll.getHeight()
                    + chatScroll.getPaddingTop() + chatScroll.getPaddingBottom();
            if (target > 0) {
                chatScroll.scrollTo(0, target);
            }
        });
    }

    private void fadeInRow(View row, int offsetDp) {
        row.setAlpha(0f);
        row.setTranslationY(dp(offsetDp));
        row.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private String shortTitle(String text) {
        String clean = text == null ? "" : text.replace('\n', ' ').trim();
        if (clean.length() > 16) {
            return clean.substring(0, 16) + "…";
        }
        return clean;
    }

    // ---------------------------------------------------------------------
    // Screens
    // ---------------------------------------------------------------------
    /**
     * 首次启动的会话加载态：只保留标题栏和参考稿同款渐变留白，
     * 不展示输入框与消息，点菜单“首页”或返回即可离开。
     */
    private void enterSessionNavIntro() {
        if (!gptStyle || chatView == null) {
            return;
        }
        resetConversation();
        sessionNavIntroMode = true;
        currentScreen = SCREEN_CHAT;
        hideKeyboard();
        homeView.setVisibility(View.GONE);
        incognitoView.setVisibility(View.GONE);
        voiceView.setVisibility(View.GONE);
        chatView.setVisibility(View.VISIBLE);
        stopVoiceOrb();
        if (chatScroll != null) {
            chatScroll.setVisibility(View.GONE);
        }
        View composer = chatView.findViewById(R.id.chat_composer);
        if (composer != null) {
            composer.setVisibility(View.GONE);
        }
        View attachStrip = chatView.findViewById(R.id.chat_attach_strip);
        if (attachStrip != null) {
            attachStrip.setVisibility(View.GONE);
        }
        View root = findViewById(R.id.root);
        if (root != null) {
            root.setBackground(new GptNavBackdropDrawable());
        }
    }

    private void exitSessionNavIntro() {
        if (!sessionNavIntroMode) {
            return;
        }
        sessionNavIntroMode = false;
        if (prefs != null) {
            prefs.setSessionNavIntroShown(true);
        }
        if (chatScroll != null) {
            chatScroll.setVisibility(View.VISIBLE);
        }
        View composer = chatView.findViewById(R.id.chat_composer);
        if (composer != null) {
            composer.setVisibility(View.VISIBLE);
        }
        View root = findViewById(R.id.root);
        if (root != null && gptStyle) {
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.gpt_bg));
        }
    }

    private void showHome() {
        exitSessionNavIntro();
        if (gptNavController != null) {
            gptNavController.dismissPopups();
        }
        if (chatSending) {
            stopChatGeneration();
        }
        currentScreen = SCREEN_HOME;
        hideKeyboard();
        homeView.setVisibility(View.VISIBLE);
        incognitoView.setVisibility(View.GONE);
        voiceView.setVisibility(View.GONE);
        chatView.setVisibility(View.GONE);
        stopVoiceOrb();
        handler.removeCallbacks(connectionFailure);
        refreshHomeApiHint();
    }

    private void showIncognito() {
        exitSessionNavIntro();
        if (gptNavController != null) {
            gptNavController.dismissPopups();
        }
        currentScreen = SCREEN_INCOGNITO;
        hideKeyboard();
        homeView.setVisibility(View.GONE);
        incognitoView.setVisibility(View.VISIBLE);
        voiceView.setVisibility(View.GONE);
        chatView.setVisibility(View.GONE);
        stopVoiceOrb();
        handler.removeCallbacks(connectionFailure);
        incognitoInput.clearFocus();
    }

    private void showChatScreen() {
        exitSessionNavIntro();
        currentScreen = SCREEN_CHAT;
        hideKeyboard();
        homeView.setVisibility(View.GONE);
        incognitoView.setVisibility(View.GONE);
        voiceView.setVisibility(View.GONE);
        chatView.setVisibility(View.VISIBLE);
        stopVoiceOrb();
        handler.removeCallbacks(connectionFailure);
        scrollChatToBottom();
    }

    private void showVoice() {
        exitSessionNavIntro();
        if (gptNavController != null) {
            gptNavController.dismissPopups();
        }
        voiceReturnScreen = currentScreen;
        currentScreen = SCREEN_VOICE;
        hideKeyboard();
        homeView.setVisibility(View.GONE);
        incognitoView.setVisibility(View.GONE);
        chatView.setVisibility(View.GONE);
        voiceView.setVisibility(View.VISIBLE);

        handler.removeCallbacks(connectionFailure);
        if (gptStyle) {
            startVoiceOrb();
        }
        if (gptStyle || dsStyle) {
            // ChatGPT / DeepSeek 参考图的语音页只有中央图形与底部操作，不显示连接状态卡片。
            voiceErrorCard.setVisibility(View.GONE);
            voiceFailed.setVisibility(View.GONE);
            voiceConnecting.setVisibility(View.GONE);
        } else {
            voiceErrorCard.setVisibility(View.VISIBLE);
            voiceFailed.setVisibility(View.GONE);
            voiceConnecting.setVisibility(View.VISIBLE);
            handler.postDelayed(connectionFailure, 2400);
        }
    }

    private void closeVoice() {
        if (voiceReturnScreen == SCREEN_CHAT) {
            showChatScreen();
        } else if (voiceReturnScreen == SCREEN_INCOGNITO) {
            showIncognito();
        } else {
            showHome();
        }
    }

    // ---------------------------------------------------------------------
    // Drawer
    // ---------------------------------------------------------------------
    private void setupDrawer() {
        drawerScrim.setOnClickListener(v -> closeDrawer());
        drawerPanel.findViewById(R.id.drawer_close).setOnClickListener(v -> closeDrawer());
        View settingsButton = drawerPanel.findViewById(R.id.drawer_settings);
        if (gptStyle) {
            // ChatGPT 抽屉右上角是搜索；设置入口放在底部头像上。
            settingsButton.setOnClickListener(v -> toggleDrawerSearch());
            View avatar = drawerPanel.findViewById(R.id.gpt_avatar);
            if (avatar != null) {
                avatar.setOnClickListener(v -> {
                    closeDrawer();
                    openSheet(SHEET_SETTINGS);
                });
            }
            View scheduled = drawerPanel.findViewById(R.id.gpt_nav_scheduled);
            if (scheduled != null) {
                scheduled.setOnClickListener(v -> Toast.makeText(this,
                        R.string.gpt_section_todo, Toast.LENGTH_SHORT).show());
            }
            View plugins = drawerPanel.findViewById(R.id.gpt_nav_plugins);
            if (plugins != null) {
                plugins.setOnClickListener(v -> Toast.makeText(this,
                        R.string.gpt_section_todo, Toast.LENGTH_SHORT).show());
            }
            drawerSearchInput = drawerPanel.findViewById(R.id.drawer_search_input);
            if (drawerSearchInput != null) {
                drawerSearchInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        buildRecentList();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });
            }
        } else if (dsStyle) {
            // DeepSeek 抽屉：搜索框常驻，齿轮与底部头像都进入设置。
            settingsButton.setOnClickListener(v -> {
                closeDrawer();
                openSheet(SHEET_SETTINGS);
            });
            View avatar = drawerPanel.findViewById(R.id.ds_avatar);
            if (avatar != null) {
                avatar.setOnClickListener(v -> {
                    closeDrawer();
                    openSheet(SHEET_SETTINGS);
                });
            }
            drawerSearchInput = drawerPanel.findViewById(R.id.drawer_search_input);
            if (drawerSearchInput != null) {
                drawerSearchInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        buildRecentList();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });
            }
        } else {
            settingsButton.setOnClickListener(v -> {
                closeDrawer();
                openSheet(SHEET_SETTINGS);
            });
        }
        drawerPanel.findViewById(R.id.drawer_new_chat).setOnClickListener(v -> {
            closeDrawer();
            resetConversation();
            showHome();
        });
        drawerPanel.findViewById(R.id.drawer_nav_chat).setOnClickListener(v -> {
            closeDrawer();
            if (gptStyle) {
                Toast.makeText(this, R.string.gpt_section_todo, Toast.LENGTH_SHORT).show();
            } else {
                showHome();
            }
        });
        drawerPanel.findViewById(R.id.drawer_nav_projects).setOnClickListener(v -> {
            closeDrawer();
            if (gptStyle) {
                Toast.makeText(this, R.string.gpt_section_todo, Toast.LENGTH_SHORT).show();
            }
        });
        drawerPanel.findViewById(R.id.drawer_nav_code).setOnClickListener(v -> {
            closeDrawer();
            if (gptStyle) {
                Toast.makeText(this, R.string.gpt_section_todo, Toast.LENGTH_SHORT).show();
            }
        });
        drawerPanel.findViewById(R.id.drawer_nav_artifacts).setOnClickListener(v -> {
            closeDrawer();
            if (gptStyle) {
                Toast.makeText(this, R.string.gpt_section_todo, Toast.LENGTH_SHORT).show();
            }
        });
        buildRecentList();
    }

    private void toggleDrawerSearch() {
        if (drawerSearchInput == null) {
            return;
        }
        View box = drawerPanel.findViewById(R.id.drawer_search_box);
        if (box == null) {
            return;
        }
        boolean show = box.getVisibility() != View.VISIBLE;
        box.setVisibility(show ? View.VISIBLE : View.GONE);
        drawerSearchInput.setText("");
        if (show) {
            drawerSearchInput.post(() -> {
                drawerSearchInput.requestFocus();
                showKeyboard(drawerSearchInput);
            });
        } else {
            hideKeyboard();
        }
        buildRecentList();
    }

    private void buildRecentList() {
        LinearLayout list = drawerPanel.findViewById(R.id.drawer_recent_list);
        list.removeAllViews();
        List<ChatStore.Conversation> conversations = store.listConversations();
        if (dsStyle) {
            buildDsRecentList(list, conversations);
            return;
        }
        if (gptStyle) {
            buildGptRecentList(list, conversations);
            return;
        }
        if (conversations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.empty_recent);
            empty.setTextSize(14);
            empty.setPadding(dp(34), dp(10), dp(20), dp(10));
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setIncludeFontPadding(false);
            list.addView(empty);
            return;
        }
        android.util.TypedValue ripple = new android.util.TypedValue();
        final boolean hasRipple = getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true);
        for (final ChatStore.Conversation conversation : conversations) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(34), 0, dp(14), 0);
            if (hasRipple) {
                row.setBackgroundResource(ripple.resourceId);
            }
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(43)));

            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
            icon.setImageResource(conversation.starred ? R.drawable.ic_starburst : R.drawable.ic_chat);
            row.addView(icon);

            TextView text = new TextView(this);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMarginStart(dp(10));
            text.setLayoutParams(textParams);
            text.setText(conversation.title);
            text.setTextSize(15);
            text.setMaxLines(1);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setTextColor(ContextCompat.getColor(this, R.color.text_nav));
            text.setIncludeFontPadding(false);
            row.addView(text);

            row.setOnClickListener(v -> {
                closeDrawer();
                openConversation(conversation.id);
            });
            list.addView(row);
        }
    }

    /** DeepSeek 抽屉：按“今天 / 昨天 / 7天内 / 更早”分组，支持搜索过滤。 */
    private void buildDsRecentList(LinearLayout list,
                                   List<ChatStore.Conversation> conversations) {
        String query = "";
        if (drawerSearchInput != null) {
            query = drawerSearchInput.getText().toString().trim();
        }
        String lowerQuery = query.toLowerCase(Locale.getDefault());

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        long todayStart = calendar.getTimeInMillis();
        long yesterdayStart = todayStart - 24L * 60 * 60 * 1000;
        long weekStart = todayStart - 6L * 24 * 60 * 60 * 1000;

        List<ChatStore.Conversation> today = new ArrayList<>();
        List<ChatStore.Conversation> yesterday = new ArrayList<>();
        List<ChatStore.Conversation> week = new ArrayList<>();
        List<ChatStore.Conversation> earlier = new ArrayList<>();
        for (ChatStore.Conversation conversation : conversations) {
            String title = conversation.title == null ? "" : conversation.title;
            if (!lowerQuery.isEmpty()
                    && !title.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                continue;
            }
            if (conversation.updatedAt >= todayStart) {
                today.add(conversation);
            } else if (conversation.updatedAt >= yesterdayStart) {
                yesterday.add(conversation);
            } else if (conversation.updatedAt >= weekStart) {
                week.add(conversation);
            } else {
                earlier.add(conversation);
            }
        }
        if (today.isEmpty() && yesterday.isEmpty() && week.isEmpty() && earlier.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(query.isEmpty() ? R.string.ds_empty_recent : R.string.ds_search_empty);
            empty.setTextSize(14);
            empty.setPadding(dp(14), dp(18), dp(14), dp(10));
            empty.setTextColor(ContextCompat.getColor(this, R.color.ds_text_tertiary));
            empty.setIncludeFontPadding(false);
            list.addView(empty);
            return;
        }
        addDsSection(list, getString(R.string.ds_section_today), today);
        addDsSection(list, getString(R.string.ds_section_yesterday), yesterday);
        addDsSection(list, getString(R.string.ds_section_week), week);
        addDsSection(list, getString(R.string.ds_section_earlier), earlier);
    }

    private void addDsSection(LinearLayout list, String title,
                              List<ChatStore.Conversation> items) {
        if (items.isEmpty()) {
            return;
        }
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(13);
        header.setTextColor(ContextCompat.getColor(this, R.color.ds_text_secondary));
        header.setIncludeFontPadding(false);
        header.setPadding(dp(14), dp(14), dp(10), dp(6));
        list.addView(header);
        for (ChatStore.Conversation conversation : items) {
            addDsConversationRow(list, conversation);
        }
    }

    private void addDsConversationRow(LinearLayout list,
                                      final ChatStore.Conversation conversation) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.setMargins(0, 0, 0, dp(2));
        row.setLayoutParams(params);
        row.setPadding(dp(14), 0, dp(12), 0);
        if (currentScreen == SCREEN_CHAT && currentConversationId == conversation.id) {
            row.setBackgroundResource(R.drawable.bg_ds_selected_row);
        } else {
            android.util.TypedValue ripple = new android.util.TypedValue();
            if (getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true)) {
                row.setBackgroundResource(ripple.resourceId);
            }
        }

        TextView text = new TextView(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text.setText(conversation.title);
        text.setTextSize(14.5f);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setTextColor(ContextCompat.getColor(this, R.color.ds_text));
        text.setIncludeFontPadding(false);
        row.addView(text);

        // 参考图中选中行右侧有三点，用作重命名/删除入口。
        ImageButton more = new ImageButton(this);
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        more.setBackgroundResource(0);
        more.setPadding(dp(6), dp(6), dp(6), dp(6));
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        more.setImageResource(R.drawable.ic_more_vertical);
        more.setColorFilter(ContextCompat.getColor(this, R.color.ds_text_secondary));
        more.setContentDescription(getString(R.string.chat_more));
        more.setOnClickListener(v -> showConversationMenu(conversation));
        row.addView(more);

        row.setOnClickListener(v -> {
            closeDrawer();
            openConversation(conversation.id);
        });
        list.addView(row);
    }

    private void showConversationMenu(final ChatStore.Conversation conversation) {
        final String[] items = {getString(R.string.rename), getString(R.string.delete)};
        dialogBuilder()
                .setTitle(conversation.title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        final EditText input = new EditText(this);
                        input.setText(conversation.title);
                        input.setSelection(input.getText().length());
                        dialogBuilder()
                                .setTitle(R.string.rename)
                                .setView(input)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.confirm, (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (!name.isEmpty()) {
                                        store.renameConversation(conversation.id, name);
                                        if (currentConversationId == conversation.id) {
                                            chatTitleView.setText(name);
                                        }
                                        buildRecentList();
                                    }
                                })
                                .show();
                    } else {
                        dialogBuilder()
                                .setTitle(R.string.delete)
                                .setMessage(R.string.delete_confirm)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.confirm, (d, w) -> {
                                    store.deleteConversation(conversation.id);
                                    if (currentConversationId == conversation.id) {
                                        resetConversation();
                                        showHome();
                                    }
                                    buildRecentList();
                                })
                                .show();
                    }
                })
                .show();
    }

    /** ChatGPT 抽屉：按“置顶 / 聊天”分组，支持搜索过滤，选中会话带浅灰底。 */
    private void buildGptRecentList(LinearLayout list,
                                    List<ChatStore.Conversation> conversations) {
        String query = "";
        if (drawerSearchInput != null
                && drawerSearchInput.getVisibility() == View.VISIBLE) {
            query = drawerSearchInput.getText().toString().trim();
        }
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        List<ChatStore.Conversation> starred = new ArrayList<>();
        List<ChatStore.Conversation> normal = new ArrayList<>();
        for (ChatStore.Conversation conversation : conversations) {
            String title = conversation.title == null ? "" : conversation.title;
            if (!lowerQuery.isEmpty()
                    && !title.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                continue;
            }
            if (conversation.starred) {
                starred.add(conversation);
            } else {
                normal.add(conversation);
            }
        }
        if (starred.isEmpty() && normal.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(query.isEmpty() ? R.string.gpt_empty_recent : R.string.gpt_search_empty);
            empty.setTextSize(14);
            empty.setPadding(dp(24), dp(14), dp(24), dp(10));
            empty.setTextColor(ContextCompat.getColor(this, R.color.gpt_text_tertiary));
            empty.setIncludeFontPadding(false);
            list.addView(empty);
            return;
        }
        if (!starred.isEmpty()) {
            addGptSectionHeader(list, getString(R.string.gpt_section_pinned));
            for (ChatStore.Conversation conversation : starred) {
                addGptConversationRow(list, conversation);
            }
        }
        if (!normal.isEmpty()) {
            addGptSectionHeader(list, getString(R.string.gpt_section_chats));
            for (ChatStore.Conversation conversation : normal) {
                addGptConversationRow(list, conversation);
            }
        }
    }

    private void addGptSectionHeader(LinearLayout list, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(13);
        header.setTextColor(ContextCompat.getColor(this, R.color.gpt_text_tertiary));
        header.setIncludeFontPadding(false);
        header.setPadding(dp(24), dp(14), dp(24), dp(4));
        list.addView(header);
    }

    private void addGptConversationRow(LinearLayout list,
                                       final ChatStore.Conversation conversation) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.setMargins(dp(12), 0, dp(12), dp(2));
        row.setLayoutParams(params);
        row.setPadding(dp(12), 0, dp(12), 0);
        if (currentScreen == SCREEN_CHAT && currentConversationId == conversation.id) {
            row.setBackgroundResource(R.drawable.bg_gpt_selected_row);
        } else {
            android.util.TypedValue ripple = new android.util.TypedValue();
            if (getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true)) {
                row.setBackgroundResource(ripple.resourceId);
            }
        }

        TextView text = new TextView(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text.setText(conversation.title);
        text.setTextSize(15);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setTextColor(ContextCompat.getColor(this, R.color.gpt_text));
        text.setIncludeFontPadding(false);
        row.addView(text);

        row.setOnClickListener(v -> {
            closeDrawer();
            openConversation(conversation.id);
        });
        list.addView(row);
    }

    private void openDrawer() {
        // 以手势容器的实际状态为准：关闭动画进行中仍允许重新滑出/点开。
        if (drawerSwipe != null && drawerSwipe.isDrawerOpen()) {
            return;
        }
        hideKeyboard();
        if (gptNavController != null) {
            // 避免会话页遗留的小弹层浮在抽屉之上。
            gptNavController.dismissPopups();
        }
        buildRecentList();
        drawerOpen = true;
        resizeDrawer();
        if (drawerSwipe != null) {
            drawerSwipe.open();
        }
    }

    private void closeDrawer() {
        if (!drawerOpen) {
            return;
        }
        if (gptStyle || dsStyle) {
            hideKeyboard();
        }
        drawerOpen = false;
        if (drawerSwipe != null) {
            drawerSwipe.close();
        }
    }

    // ---------------------------------------------------------------------
    // Bottom sheets
    // ---------------------------------------------------------------------
    private void setupSheetLayer() {
        sheetScrim.setOnClickListener(v -> closeSheet());
    }

    private void openSheet(int mode) {
        hideKeyboard();
        // 从附件面板直接切到其它面板时，先把被隐藏的输入条恢复。
        if (sheetMode == SHEET_ATTACH && gptStyle) {
            setAttachComposersVisible(true);
        }
        if (drawerSwipe != null) {
            drawerSwipe.setSheetOpen(true);
        }
        sheetHost.removeAllViews();
        sheetMode = mode;
        LayoutInflater inflater = LayoutInflater.from(this);
        View content;
        if (mode == SHEET_FULL) {
            content = inflater.inflate(styleLayout(R.layout.sheet_models_full,
                    R.layout.sheet_models_full_gpt, R.layout.sheet_models_full_ds),
                    sheetHost, false);
            fullSheet = content;
            bindFullSheet(content);
        } else if (mode == SHEET_COMPACT) {
            content = inflater.inflate(styleLayout(R.layout.sheet_models_compact,
                    R.layout.sheet_models_compact_gpt, R.layout.sheet_models_compact_ds),
                    sheetHost, false);
            compactSheet = content;
            bindCompactSheet(content);
        } else if (mode == SHEET_ATTACH) {
            content = inflater.inflate(styleLayout(R.layout.sheet_attach,
                    R.layout.sheet_attach_gpt, R.layout.sheet_attach_ds),
                    sheetHost, false);
            bindAttachSheet(content);
        } else if (mode == SHEET_ICON) {
            content = inflater.inflate(styleLayout(R.layout.sheet_app_icon,
                    R.layout.sheet_app_icon_gpt, R.layout.sheet_app_icon_ds),
                    sheetHost, false);
            bindIconSheet(content);
        } else {
            content = inflater.inflate(styleLayout(R.layout.sheet_settings,
                    R.layout.sheet_settings_gpt, R.layout.sheet_settings_ds),
                    sheetHost, false);
            bindSettingsSheet(content);
        }
        // 附件面板的圆角画在布局内的白色卡片上，根容器保持透明只负责定位。
        content.setElevation(mode == SHEET_ATTACH && gptStyle ? dp(0) : dp(14));
        // 模型面板与主界面同底色；设置/图标面板用带分组卡片的分区底色；
        // 附件面板按皮肤使用各自的面板材质（Claude 米白、DeepSeek/ChatGPT 纯白大圆角）。
        if (mode == SHEET_ATTACH) {
            if (gptStyle) {
                // ChatGPT 附件面板的白色圆角卡片画在布局内层，根容器必须保持透明，
                // 否则矩形底色会盖住参考图里的圆角。
                content.setBackground(null);
            } else {
                content.setBackgroundResource(dsStyle ? R.drawable.bg_ds_attach_sheet
                        : R.drawable.bg_sheet_white);
            }
        } else if (gptStyle) {
            content.setBackgroundResource(mode == SHEET_FULL || mode == SHEET_COMPACT
                    ? R.drawable.bg_gpt_sheet : R.drawable.bg_gpt_sheet_alt);
        } else if (dsStyle) {
            content.setBackgroundResource(mode == SHEET_FULL || mode == SHEET_COMPACT
                    ? R.drawable.bg_ds_sheet : R.drawable.bg_ds_sheet_alt);
        } else {
            content.setBackgroundResource(R.drawable.bg_sheet);
        }
        // 面板宿主改为全屏后需要显式贴底，保持原有底部面板的定位。
        FrameLayout.LayoutParams contentParams =
                (FrameLayout.LayoutParams) content.getLayoutParams();
        contentParams.gravity = Gravity.BOTTOM | Gravity.START;
        if (mode == SHEET_ATTACH && gptStyle) {
            // 按参考图比例：白卡片 510x636px（360dp 屏上 240x298dp），
            // 距屏幕左 32dp、距底 14dp，卡片保持纯净的独立悬浮形态。
            contentParams.setMargins(dp(32), 0, 0, dp(14));
        }
        content.setLayoutParams(contentParams);
        if (mode == SHEET_ATTACH && gptStyle) {
            // 主界面保持参考图的干净浅色底，不再叠加整屏暗色投影；
            // 展开附件面板时只收起底部输入条。
            setAttachComposersVisible(false);
        }
        sheetHost.addView(content);
        limitSheetHeight(content);

        sheetLayer.setVisibility(View.VISIBLE);
        // 附件面板是三套独立交互：Claude 保留深灰遮罩，DeepSeek/ChatGPT 用很淡的遮罩，
        // 更接近各自参考图中“主界面几乎不被压暗”的观感。
        int scrimColor;
        if (mode == SHEET_COMPACT) {
            scrimColor = 0x75000000;
        } else if (mode == SHEET_ATTACH) {
            // 参考图主界面保持原本的浅色底，不额外压暗。
            scrimColor = dsStyle ? 0x26000000 : (gptStyle ? 0x00000000 : 0x52000000);
        } else {
            scrimColor = gptStyle ? 0x80000000 : (dsStyle ? 0x4D000000 : 0x52000000);
        }
        sheetScrim.setBackgroundColor(scrimColor);
        sheetScrim.setAlpha(0f);
        // 小卡片从底部滑入，位移不需要像整屏抽屉那样大。
        content.setTranslationY(mode == SHEET_ATTACH && gptStyle ? dp(120) : dp(320));
        content.animate().translationY(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
        sheetScrim.animate().alpha(1f).setDuration(280).start();
    }

    /** 打开 ChatGPT 附件面板时隐藏底部输入条与附件预览，关闭时恢复。 */
    private void setAttachComposersVisible(boolean visible) {
        if (!gptStyle) {
            return;
        }
        int[] ids = {R.id.home_composer, R.id.chat_composer,
                R.id.incognito_composer, R.id.voice_bottom_row};
        View[] screens = {homeView, chatView, incognitoView, voiceView};
        for (int i = 0; i < ids.length; i++) {
            if (screens[i] == null) {
                continue;
            }
            View composer = screens[i].findViewById(ids[i]);
            if (composer != null) {
                composer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
        }

        // 附件预览条叠在输入条上方；只隐藏输入条会让已有附件浮在面板后方。
        int[] stripIds = {R.id.home_attach_strip, R.id.chat_attach_strip,
                R.id.incognito_attach_strip};
        for (int i = 0; i < stripIds.length; i++) {
            if (screens[i] == null) {
                continue;
            }
            View strip = screens[i].findViewById(stripIds[i]);
            if (strip != null) {
                strip.setVisibility(visible && !pendingAttachments.isEmpty()
                        ? View.VISIBLE : View.GONE);
            }
        }
    }

    /** 设置面板内容较多时限制最大高度并允许滚动，避免超出屏幕或被键盘顶出可视区域。 */
    private void limitSheetHeight(final View content) {
        if (!(content instanceof ScrollView)) {
            return;
        }
        content.post(() -> {
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            View root = findViewById(R.id.root);
            if (root != null) {
                int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
                if (available > 0) {
                    maxHeight = Math.min(maxHeight, (int) (available * 0.94f));
                }
            }
            // 先恢复自然高度重新测量，再按当前可用空间收窄；
            // 这样键盘收起后面板还能重新展开，不会一直卡在键盘弹出时的高度。
            ViewGroup.LayoutParams params = content.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setLayoutParams(params);
            final int limit = Math.max(maxHeight, dp(220));
            content.post(() -> {
                if (content.getMeasuredHeight() > limit) {
                    ViewGroup.LayoutParams narrowed = content.getLayoutParams();
                    narrowed.height = limit;
                    content.setLayoutParams(narrowed);
                }
            });
        });
    }

    /** 输入法弹出/收起后，重新计算设置面板高度，保证顶部 API 输入框仍在屏幕内。 */
    private void adjustSheetHeightForIme() {
        if (sheetMode != SHEET_SETTINGS || sheetHost == null) {
            return;
        }
        View content = sheetHost.getChildAt(sheetHost.getChildCount() - 1);
        if (content != null) {
            content.post(() -> limitSheetHeight(content));
        }
    }

    /** 设置面板的输入框获得焦点后，等键盘动画结束再把它滚动到可见区域。 */
    private void scrollSheetFieldIntoView(final View field) {
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                return;
            }
            v.postDelayed(() -> {
                if (v.getParent() == null) {
                    return;
                }
                Rect rect = new Rect(0, 0, v.getWidth(), v.getHeight() + dp(72));
                v.requestRectangleOnScreen(rect, true);
            }, 320);
        });
    }

    private void closeSheet() {
        if (sheetMode == SHEET_NONE) {
            return;
        }
        // 设置面板里输入过内容后关闭时把键盘一并收起，
        // 否则键盘会残留在下面的页面上，看起来像输入框点不动。
        hideKeyboard();
        if (sheetMode == SHEET_ATTACH && gptStyle) {
            // 附件面板关闭后恢复底部输入条。
            setAttachComposersVisible(true);
        }
        if (sheetMode == SHEET_SETTINGS) {
            // 直接点遮罩关闭时也保存已填写的配置，避免用户误以为没生效
            View content = sheetHost.getChildAt(sheetHost.getChildCount() - 1);
            if (content != null) {
                EditText key = content.findViewById(R.id.settings_api_key);
                EditText base = content.findViewById(R.id.settings_base_url);
                EditText model = content.findViewById(R.id.settings_model);
                if (key != null && base != null && model != null
                        && key.getText().toString().trim().length() > 0) {
                    saveApiConfig(key, base, model);
                }
            }
        }
        sheetMode = SHEET_NONE;
        // 最后一个子 View 是面板本体。
        final View content = sheetHost.getChildAt(sheetHost.getChildCount() - 1);
        sheetScrim.animate().alpha(0f).setDuration(220).start();
        if (content != null) {
            for (int i = 0; i < sheetHost.getChildCount(); i++) {
                View child = sheetHost.getChildAt(i);
                if (child != content) {
                    child.animate().alpha(0f).setDuration(220).start();
                }
            }
            content.animate().translationY(content.getHeight()).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        sheetHost.removeAllViews();
                        sheetLayer.setVisibility(View.GONE);
                        if (drawerSwipe != null) {
                            drawerSwipe.setSheetOpen(false);
                        }
                    }).start();
        } else {
            sheetLayer.setVisibility(View.GONE);
            if (drawerSwipe != null) {
                drawerSwipe.setSheetOpen(false);
            }
        }
    }

    private String displayModelName(String internal, boolean compact) {
        String[] source = compact ? COMPACT_MODELS : FULL_MODELS;
        String[] target = compact
                ? (dsStyle ? DS_COMPACT_LABELS : (gptStyle ? GPT_COMPACT_LABELS : null))
                : (dsStyle ? DS_FULL_LABELS : (gptStyle ? GPT_FULL_LABELS : null));
        if (target == null) {
            return internal;
        }
        for (int i = 0; i < source.length && i < target.length; i++) {
            if (source[i].equals(internal)) {
                return target[i];
            }
        }
        return internal;
    }

    private void bindFullSheet(View sheet) {
        sheet.findViewById(R.id.full_close).setOnClickListener(v -> closeSheet());
        TextView note = sheet.findViewById(R.id.full_note);
        if (dsStyle || gptStyle) {
            note.setText(R.string.model_display_note);
        } else {
            applyNoteLink(note);
        }

        int[] titleIds = {R.id.full_title_fable, R.id.full_title_opus,
                R.id.full_title_sonnet, R.id.full_title_haiku};
        for (int i = 0; i < titleIds.length; i++) {
            final String name = FULL_MODELS[i];
            TextView title = sheet.findViewById(titleIds[i]);
            if (title != null && (dsStyle || gptStyle)) {
                title.setText(displayModelName(name, false));
            }
            View item = (View) title.getParent();
            item.setOnClickListener(v -> applyFullModel(name));
        }
        updateFullChecks();
    }

    private void bindCompactSheet(View sheet) {
        sheet.findViewById(R.id.compact_close).setOnClickListener(v -> closeSheet());

        int[] titleIds = {R.id.compact_title_fable, R.id.compact_title_sonnet,
                R.id.compact_title_haiku};
        for (int i = 0; i < titleIds.length; i++) {
            final String name = COMPACT_MODELS[i];
            TextView title = sheet.findViewById(titleIds[i]);
            if (title != null && (dsStyle || gptStyle)) {
                title.setText(displayModelName(name, true));
            }
            View item = (View) title.getParent();
            item.setOnClickListener(v -> applyCompactModel(name));
        }
        updateCompactChecks();

        final View toggle = sheet.findViewById(R.id.compact_toggle);
        final View thumb = sheet.findViewById(R.id.compact_toggle_thumb);
        updateToggle(toggle, thumb, extendOn, dp(3));
        toggle.setOnClickListener(v -> {
            extendOn = !extendOn;
            prefs.setExtendOn(extendOn);
            updateToggle(toggle, thumb, extendOn, dp(3));
        });
    }

    private void bindSettingsSheet(View sheet) {
        sheet.findViewById(R.id.settings_close).setOnClickListener(v -> closeSheet());
        bindStyleOptions(sheet);
        TextView about = sheet.findViewById(R.id.settings_about);
        if (about != null) {
            if (gptStyle) {
                about.setText(R.string.set_about_gpt);
            } else if (dsStyle) {
                about.setText(R.string.ds_about);
            }
        }

        final EditText keyInput = sheet.findViewById(R.id.settings_api_key);
        final EditText baseInput = sheet.findViewById(R.id.settings_base_url);
        final EditText modelInput = sheet.findViewById(R.id.settings_model);
        final TextView status = sheet.findViewById(R.id.settings_status);
        final TextView showKey = sheet.findViewById(R.id.settings_api_show);
        final TextView testButton = sheet.findViewById(R.id.settings_test);

        keyInput.setText(prefs.apiKey());
        baseInput.setText(prefs.baseUrl());
        modelInput.setText(prefs.model());
        scrollSheetFieldIntoView(keyInput);
        scrollSheetFieldIntoView(baseInput);
        scrollSheetFieldIntoView(modelInput);

        final boolean[] keyVisible = {false};
        showKey.setOnClickListener(v -> {
            keyVisible[0] = !keyVisible[0];
            keyInput.setInputType(keyVisible[0]
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            keyInput.setSelection(keyInput.getText().length());
            showKey.setText(keyVisible[0] ? R.string.api_hide : R.string.api_show);
        });

        sheet.findViewById(R.id.settings_model_flash).setOnClickListener(v -> {
            modelInput.setText(Prefs.DEFAULT_MODEL);
            refreshModelChips(sheet, modelInput);
            refreshModelSelection(sheet, modelInput);
        });
        sheet.findViewById(R.id.settings_model_chat).setOnClickListener(v -> {
            modelInput.setText("deepseek-chat");
            refreshModelChips(sheet, modelInput);
            refreshModelSelection(sheet, modelInput);
        });
        refreshModelChips(sheet, modelInput);
        bindModelPicker(sheet, keyInput, baseInput, modelInput);

        sheet.findViewById(R.id.settings_save).setOnClickListener(v -> {
            saveApiConfig(keyInput, baseInput, modelInput);
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.api_saved);
            Toast.makeText(this, R.string.api_saved, Toast.LENGTH_SHORT).show();
        });

        testButton.setOnClickListener(v -> {
            final String key = keyInput.getText().toString().trim();
            final String base = baseInput.getText().toString().trim();
            final String model = modelInput.getText().toString().trim();
            if (key.isEmpty()) {
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.api_invalid);
                return;
            }
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.api_testing);
            testButton.setEnabled(false);
            final String effectiveModel = model.isEmpty() ? Prefs.DEFAULT_MODEL : model;
            final String effectiveBase = base.isEmpty() ? Prefs.DEFAULT_BASE_URL : base;
            final AtomicReference<String> used = new AtomicReference<>(effectiveModel);
            client.ping(key, effectiveBase, effectiveModel, Prefs.FALLBACK_MODEL,
                    new DeepSeekClient.Callback() {
                        @Override
                        public void onDelta(String delta) {
                        }

                        @Override
                        public void onDone(String full) {
                            runOnUiThread(() -> {
                                testButton.setEnabled(true);
                                String actual = used.get();
                                if (!actual.equals(effectiveModel)) {
                                    // 记录实际可用的模型，避免每次对话都先失败一次
                                    modelInput.setText(actual);
                                }
                                saveApiConfig(keyInput, baseInput, modelInput);
                                status.setText(getString(R.string.api_test_ok)
                                        + "（" + actual + "）");
                            });
                        }

                        @Override
                        public void onError(String message, int code) {
                            runOnUiThread(() -> {
                                testButton.setEnabled(true);
                                status.setText(getString(R.string.api_test_fail) + "：" + message);
                            });
                        }

                        @Override
                        public void onModelSwitched(String switchedModel) {
                            used.set(switchedModel);
                        }
                    });
        });

        updateToggle(sheet.findViewById(R.id.settings_toggle_sound),
                sheet.findViewById(R.id.settings_thumb_sound), soundOn, dp(3));
        updateToggle(sheet.findViewById(R.id.settings_toggle_haptic),
                sheet.findViewById(R.id.settings_thumb_haptic), hapticOn, dp(3));

        final View soundTrack = sheet.findViewById(R.id.settings_toggle_sound);
        final View soundThumb = sheet.findViewById(R.id.settings_thumb_sound);
        soundTrack.setOnClickListener(v -> {
            soundOn = !soundOn;
            prefs.setSoundOn(soundOn);
            updateToggle(soundTrack, soundThumb, soundOn, dp(3));
        });
        final View hapticTrack = sheet.findViewById(R.id.settings_toggle_haptic);
        final View hapticThumb = sheet.findViewById(R.id.settings_thumb_haptic);
        hapticTrack.setOnClickListener(v -> {
            hapticOn = !hapticOn;
            prefs.setHapticOn(hapticOn);
            updateToggle(hapticTrack, hapticThumb, hapticOn, dp(3));
        });

        // 桌面图标入口：先保存当前 API 配置，再切到图标面板（面板会复用同一个底部容器）
        View iconRow = sheet.findViewById(R.id.settings_icon);
        if (iconRow != null) {
            ImageView iconPreview = sheet.findViewById(R.id.settings_icon_preview);
            if (iconPreview != null) {
                iconPreview.setImageResource(iconPreviewRes(prefs.iconStyle()));
            }
            iconRow.setOnClickListener(v -> {
                saveApiConfig(keyInput, baseInput, modelInput);
                openSheet(SHEET_ICON);
            });
        }

        View clear = sheet.findViewById(R.id.settings_clear);
        if (clear != null) {
            clear.setOnClickListener(v -> dialogBuilder()
                    .setTitle(R.string.set_clear)
                    .setMessage(R.string.clear_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                        store.clearAll();
                        resetConversation();
                        buildRecentList();
                        showHome();
                        closeSheet();
                        Toast.makeText(this, R.string.storage_cleared,
                                Toast.LENGTH_SHORT).show();
                    })
                    .show());
        }
    }

    /**
     * 设置页的模型列表：调用 /models 读取当前 API Key 可用的模型 id，
     * 结果按最近一次成功获取缓存到本地，重新打开设置时立即展示。
     */
    private void bindModelPicker(final View sheet, final EditText keyInput,
                                 final EditText baseInput, final EditText modelInput) {
        final TextView fetch = sheet.findViewById(R.id.settings_models_fetch);
        final TextView status = sheet.findViewById(R.id.settings_models_status);
        final LinearLayout list = sheet.findViewById(R.id.settings_model_list);
        if (fetch == null || status == null || list == null) {
            return;
        }

        final List<String> cached = cachedModelIds();
        if (!cached.isEmpty()) {
            renderModelOptions(sheet, keyInput, baseInput, modelInput, cached);
            status.setText(getString(R.string.api_models_cached, cached.size()));
        }

        // 手动改写模型名时，列表里的勾选状态同步更新。
        modelInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshModelSelection(sheet, modelInput);
            }
        });

        fetch.setOnClickListener(v -> {
            final String key = keyInput.getText().toString().trim();
            final String base = baseInput.getText().toString().trim();
            if (key.isEmpty()) {
                status.setText(R.string.api_models_need_key);
                Toast.makeText(this, R.string.api_models_need_key, Toast.LENGTH_SHORT).show();
                return;
            }
            fetch.setEnabled(false);
            fetch.setAlpha(0.6f);
            fetch.setText(R.string.api_models_fetching);
            status.setText(R.string.api_models_loading);
            // 列表展开在输入框下方，先收起键盘让用户能直接看到结果。
            hideKeyboard();
            final String effectiveBase = base.isEmpty() ? Prefs.DEFAULT_BASE_URL : base;
            client.listModels(key, effectiveBase, new DeepSeekClient.ModelsCallback() {
                @Override
                public void onModels(List<String> ids) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || sheet.getParent() == null) {
                            return;
                        }
                        fetch.setEnabled(true);
                        fetch.setAlpha(1f);
                        fetch.setText(R.string.api_models_fetch);
                        prefs.setCachedModels(joinModels(ids));
                        if (ids.isEmpty()) {
                            LinearLayout listView = sheet.findViewById(R.id.settings_model_list);
                            if (listView != null) {
                                listView.removeAllViews();
                                listView.setVisibility(View.GONE);
                            }
                            status.setText(R.string.api_models_empty);
                            return;
                        }
                        renderModelOptions(sheet, keyInput, baseInput, modelInput, ids);
                        status.setText(getString(R.string.api_models_loaded, ids.size()));
                        // 列表展开后内容变高，重新按屏幕空间限制面板高度。
                        limitSheetHeight(sheet);
                    });
                }

                @Override
                public void onError(String message, int code) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || sheet.getParent() == null) {
                            return;
                        }
                        fetch.setEnabled(true);
                        fetch.setAlpha(1f);
                        fetch.setText(R.string.api_models_fetch);
                        status.setText(getString(R.string.api_models_fail, message));
                        Toast.makeText(MainActivity.this,
                                getString(R.string.api_models_fail, message),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private List<String> cachedModelIds() {
        List<String> ids = new ArrayList<>();
        String raw = prefs.cachedModels();
        if (raw == null || raw.trim().isEmpty()) {
            return ids;
        }
        for (String part : raw.split("\n")) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String joinModels(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(id.trim());
        }
        return builder.toString();
    }

    /** 把 /models 返回的 id 渲染成可点选的行，点一下即填入并保存为当前模型。 */
    private void renderModelOptions(final View sheet, final EditText keyInput,
                                    final EditText baseInput, final EditText modelInput,
                                    final List<String> models) {
        final LinearLayout list = sheet.findViewById(R.id.settings_model_list);
        final TextView status = sheet.findViewById(R.id.settings_models_status);
        if (list == null) {
            return;
        }
        list.removeAllViews();
        if (models == null || models.isEmpty()) {
            list.setVisibility(View.GONE);
            return;
        }
        list.setVisibility(View.VISIBLE);
        for (int i = 0; i < models.size(); i++) {
            final String id = models.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(10), 0);
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            row.setTag(id);

            TextView label = new TextView(this);
            label.setText(id);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, dsStyle ? 13f : 13.5f);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(ContextCompat.getColor(this,
                    dsStyle ? R.color.ds_brand
                            : (gptStyle ? R.color.gpt_blue : R.color.text_primary)));
            LinearLayout.LayoutParams checkParams =
                    new LinearLayout.LayoutParams(dp(18), dp(18));
            checkParams.setMarginStart(dp(8));
            row.addView(check, checkParams);

            row.setOnClickListener(v -> {
                modelInput.setText(id);
                modelInput.setSelection(modelInput.getText().length());
                saveApiConfig(keyInput, baseInput, modelInput);
                refreshModelChips(sheet, modelInput);
                refreshModelSelection(sheet, modelInput);
                if (status != null) {
                    status.setText(getString(R.string.api_models_selected, id));
                }
                Toast.makeText(this, getString(R.string.api_models_switched, id),
                        Toast.LENGTH_SHORT).show();
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

            if (i < models.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this,
                        dsStyle ? R.color.ds_divider
                                : (gptStyle ? R.color.gpt_divider : R.color.divider_card)));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.setMarginStart(dp(10));
                dividerParams.setMarginEnd(dp(10));
                list.addView(divider, dividerParams);
            }
        }
        refreshModelSelection(sheet, modelInput);
    }

    /** 模型列表的选中态：当前输入框里的 id 对应行显示强调色与勾。 */
    private void refreshModelSelection(View sheet, EditText modelInput) {
        if (sheet == null) {
            return;
        }
        LinearLayout list = sheet.findViewById(R.id.settings_model_list);
        if (list == null) {
            return;
        }
        String current = modelInput == null
                ? prefs.model() : modelInput.getText().toString().trim();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof String) || !(child instanceof ViewGroup)) {
                continue;
            }
            ViewGroup row = (ViewGroup) child;
            if (row.getChildCount() < 2) {
                continue;
            }
            boolean selected = tag.equals(current);
            View labelView = row.getChildAt(0);
            View checkView = row.getChildAt(1);
            if (labelView instanceof TextView) {
                TextView label = (TextView) labelView;
                int color;
                if (dsStyle) {
                    color = selected ? R.color.ds_brand : R.color.ds_text;
                } else if (gptStyle) {
                    color = selected ? R.color.gpt_blue : R.color.gpt_text;
                } else {
                    color = R.color.text_primary;
                }
                label.setTextColor(ContextCompat.getColor(this, color));
                label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (checkView != null) {
                checkView.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    /** 模型快捷胶囊按输入框里当前的模型高亮，DeepSeek / ChatGPT 各用自己的选中样式。 */
    private void refreshModelChips(View sheet, EditText modelInput) {
        if (sheet == null || (!dsStyle && !gptStyle)) {
            return;
        }
        TextView flash = sheet.findViewById(R.id.settings_model_flash);
        TextView chat = sheet.findViewById(R.id.settings_model_chat);
        String model = modelInput == null
                ? prefs.model() : modelInput.getText().toString().trim();
        applyModelChip(flash, Prefs.DEFAULT_MODEL.equals(model));
        applyModelChip(chat, "deepseek-chat".equals(model));
    }

    private void applyModelChip(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        if (dsStyle) {
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_ds_pill_active : R.drawable.bg_ds_chip);
            chip.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.ds_brand : R.color.ds_text_secondary));
        } else {
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_gpt_primary : R.drawable.bg_gpt_chip);
            chip.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.card : R.color.gpt_text_secondary));
        }
    }

    /** 设置里的三套皮肤入口：只切换本地皮肤偏好，接口与聊天数据完全共用。 */
    private void bindStyleOptions(View sheet) {
        View claude = sheet.findViewById(R.id.settings_style_claude);
        View gpt = sheet.findViewById(R.id.settings_style_chatgpt);
        View ds = sheet.findViewById(R.id.settings_style_deepseek);
        View claudeCheck = sheet.findViewById(R.id.settings_style_check_claude);
        View gptCheck = sheet.findViewById(R.id.settings_style_check_chatgpt);
        View dsCheck = sheet.findViewById(R.id.settings_style_check_deepseek);
        if (claude == null || gpt == null) {
            return;
        }
        int style = prefs.uiStyle();
        // DeepSeek / ChatGPT 风格用整行底色强调当前皮肤，Claude 保持原有的纯文字勾选。
        int activeRow = dsStyle ? R.drawable.bg_ds_row_active
                : (gptStyle ? R.drawable.bg_gpt_row_active : 0);
        if (activeRow != 0) {
            View[] rows = {claude, gpt, ds};
            int[] rowStyles = {Prefs.STYLE_CLAUDE, Prefs.STYLE_CHATGPT, Prefs.STYLE_DEEPSEEK};
            for (int i = 0; i < rows.length; i++) {
                if (rows[i] == null) {
                    continue;
                }
                rows[i].setBackgroundResource(i < rowStyles.length
                        && rowStyles[i] == style ? activeRow : R.drawable.bg_row_ripple);
            }
        }
        if (claudeCheck != null) {
            claudeCheck.setVisibility(style == Prefs.STYLE_CLAUDE
                    ? View.VISIBLE : View.INVISIBLE);
        }
        if (gptCheck != null) {
            gptCheck.setVisibility(style == Prefs.STYLE_CHATGPT
                    ? View.VISIBLE : View.INVISIBLE);
        }
        if (dsCheck != null) {
            dsCheck.setVisibility(style == Prefs.STYLE_DEEPSEEK
                    ? View.VISIBLE : View.INVISIBLE);
        }
        claude.setOnClickListener(v -> switchUiStyle(Prefs.STYLE_CLAUDE));
        gpt.setOnClickListener(v -> switchUiStyle(Prefs.STYLE_CHATGPT));
        if (ds != null) {
            ds.setOnClickListener(v -> switchUiStyle(Prefs.STYLE_DEEPSEEK));
        }
    }

    private void switchUiStyle(final int style) {
        if (prefs.uiStyle() == style) {
            return;
        }
        prefs.setUiStyle(style);
        int toast = style == Prefs.STYLE_CHATGPT
                ? R.string.style_switched_chatgpt
                : (style == Prefs.STYLE_DEEPSEEK
                ? R.string.style_switched_deepseek : R.string.style_switched_claude);
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        // 三套布局结构差异较大，直接重建 Activity 让所有页面一次性换肤；
        // DeepSeek 配置和聊天记录都在本地持久化，不会因为重建丢失。
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
        }, 160);
    }

    private void saveApiConfig(EditText keyInput, EditText baseInput, EditText modelInput) {
        final String newKey = keyInput.getText().toString().trim();
        final String newBase = baseInput.getText().toString().trim();
        final String normalizedBase = newBase.isEmpty() ? Prefs.DEFAULT_BASE_URL : newBase;
        // 换了 Key 或接口地址后，旧的模型列表可能已经不可用，清掉缓存等下次重新获取。
        if (!newKey.equals(prefs.apiKey()) || !normalizedBase.equals(prefs.baseUrl())) {
            prefs.setCachedModels("");
        }
        prefs.setApiKey(newKey);
        prefs.setBaseUrl(newBase);
        prefs.setModel(modelInput.getText().toString());
        refreshHomeApiHint();
    }

    // ---------------------------------------------------------------------
    // 桌面图标：内置样式（activity-alias 切换）+ 相册图片（桌面快捷方式）
    // ---------------------------------------------------------------------
    private int sanitizeIconStyle(int style) {
        return (style >= 0 && style < ICON_COMPONENT_SUFFIX.length) ? style : 0;
    }

    private int iconPreviewRes(int style) {
        return ICON_PREVIEWS[sanitizeIconStyle(style)];
    }

    /** 每次冷启动校验一次：如果组件状态与本地保存的样式不一致就重新应用。 */
    private void syncIconStyle() {
        final int style = sanitizeIconStyle(prefs.iconStyle());
        try {
            PackageManager pm = getPackageManager();
            String pkg = getPackageName();
            boolean consistent = true;
            for (int i = 0; i < ICON_COMPONENT_SUFFIX.length; i++) {
                int state = pm.getComponentEnabledSetting(
                        new ComponentName(pkg, pkg + ICON_COMPONENT_SUFFIX[i]));
                boolean enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0);
                if (enabled != (i == style)) {
                    consistent = false;
                    break;
                }
            }
            if (!consistent) {
                applyIconStyle(style);
            }
        } catch (Exception ignored) {
            // 个别 ROM 限制了组件开关，忽略即可，不影响应用其他功能
        }
    }

    /**
     * 只启用目标图标对应的启动入口，其余全部禁用。
     * 这样桌面上始终只有一个入口，且图标就是所选样式。
     */
    private boolean applyIconStyle(int style) {
        final int target = sanitizeIconStyle(style);
        final PackageManager pm = getPackageManager();
        final String pkg = getPackageName();
        try {
            pm.setComponentEnabledSetting(
                    new ComponentName(pkg, pkg + ICON_COMPONENT_SUFFIX[target]),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            for (int i = 0; i < ICON_COMPONENT_SUFFIX.length; i++) {
                if (i == target) {
                    continue;
                }
                pm.setComponentEnabledSetting(
                        new ComponentName(pkg, pkg + ICON_COMPONENT_SUFFIX[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
            prefs.setIconStyle(target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void bindIconSheet(final View sheet) {
        sheet.findViewById(R.id.icon_close).setOnClickListener(v -> closeSheet());

        for (int i = 0; i < ICON_CELL_IDS.length; i++) {
            final int style = i;
            View cell = sheet.findViewById(ICON_CELL_IDS[i]);
            if (cell != null) {
                cell.setOnClickListener(v -> {
                    if (!applyIconStyle(style)) {
                        Toast.makeText(this, R.string.icon_style_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    refreshIconSheetSelection(sheet, style);
                    Toast.makeText(this, R.string.icon_style_changed, Toast.LENGTH_SHORT).show();
                });
            }
        }

        refreshIconSheetSelection(sheet, prefs.iconStyle());
        refreshPhotoPreview(sheet);

        View pick = sheet.findViewById(R.id.icon_pick_photo);
        if (pick != null) {
            pick.setOnClickListener(v -> pickAppIconPhoto());
        }
        final View pin = sheet.findViewById(R.id.icon_pin_photo);
        if (pin != null) {
            pin.setOnClickListener(v -> pinPhotoIconToHome(pin));
        }
        View reset = sheet.findViewById(R.id.icon_reset);
        if (reset != null) {
            reset.setOnClickListener(v -> {
                if (applyIconStyle(0)) {
                    refreshIconSheetSelection(sheet, 0);
                    Toast.makeText(this, R.string.icon_reset_done, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.icon_style_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void refreshIconSheetSelection(View sheet, int style) {
        if (sheet == null) {
            return;
        }
        final int selected = sanitizeIconStyle(style);
        for (int i = 0; i < ICON_CELL_IDS.length; i++) {
            View box = sheet.findViewById(ICON_CELL_BOX_IDS[i]);
            TextView name = sheet.findViewById(ICON_CELL_NAME_IDS[i]);
            boolean on = i == selected;
            if (box != null) {
                box.setBackgroundResource(on
                        ? (dsStyle ? R.drawable.bg_ds_icon_cell_selected
                        : (gptStyle ? R.drawable.bg_gpt_icon_cell_selected
                        : R.drawable.bg_icon_cell_selected))
                        : (dsStyle ? R.drawable.bg_ds_icon_cell
                        : (gptStyle ? R.drawable.bg_gpt_icon_cell
                        : R.drawable.bg_icon_cell)));
            }
            if (name != null) {
                int onColor = dsStyle ? R.color.ds_brand
                        : (gptStyle ? R.color.gpt_blue : R.color.orange);
                int offColor = dsStyle ? R.color.ds_text_secondary
                        : (gptStyle ? R.color.gpt_text_secondary : R.color.text_secondary);
                name.setTextColor(ContextCompat.getColor(this, on ? onColor : offColor));
                name.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
        ImageView preview = sheet.findViewById(R.id.icon_current_preview);
        if (preview != null) {
            preview.setImageResource(ICON_PREVIEWS[selected]);
        }
        TextView currentName = sheet.findViewById(R.id.icon_current_name);
        if (currentName != null) {
            currentName.setText(getString(ICON_NAME_RES[selected]));
        }
    }

    private File appIconPhotoFile() {
        return new File(getFilesDir(), "app_icon_photo.png");
    }

    private void pickAppIconPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.icon_pick_title)), REQUEST_PICK_APP_ICON);
        } catch (Exception e) {
            Toast.makeText(this, R.string.icon_picker_missing, Toast.LENGTH_SHORT).show();
        }
    }

    /** 相册图片压成正方形后写入私有目录，作为桌面快捷方式图标的来源。 */
    private void handlePickedAppIcon(final Uri uri) {
        Toast.makeText(this, R.string.icon_photo_saving, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = decodePickedImage(uri, APP_ICON_MAX_SIDE);
            Bitmap square = centerCropSquare(bitmap);
            final boolean saved = square != null && saveAppIconPhoto(square);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!saved) {
                    Toast.makeText(this, R.string.icon_photo_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                View sheet = sheetMode == SHEET_ICON ? sheetHost.getChildAt(0) : null;
                refreshPhotoPreview(sheet);
                Toast.makeText(this, R.string.icon_photo_saved, Toast.LENGTH_SHORT).show();
            });
        }, "app-icon-save").start();
    }

    private boolean saveAppIconPhoto(Bitmap bitmap) {
        Bitmap scaled = bitmap;
        int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxSide > APP_ICON_MAX_SIDE) {
            float ratio = APP_ICON_MAX_SIDE / (float) maxSide;
            int width = Math.max(1, Math.round(bitmap.getWidth() * ratio));
            int height = Math.max(1, Math.round(bitmap.getHeight() * ratio));
            scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        File target = appIconPhotoFile();
        File temp = new File(getFilesDir(), "app_icon_photo.tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temp);
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            if (target.exists() && !target.delete()) {
                return false;
            }
            return temp.renameTo(target);
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(output);
            if (scaled != bitmap) {
                scaled.recycle();
            }
            if (temp.exists() && !temp.equals(target)) {
                temp.delete();
            }
        }
    }

    private Bitmap centerCropSquare(Bitmap src) {
        if (src == null) {
            return null;
        }
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) {
            return null;
        }
        if (size == src.getWidth() && size == src.getHeight()) {
            return src;
        }
        int left = (src.getWidth() - size) / 2;
        int top = (src.getHeight() - size) / 2;
        return Bitmap.createBitmap(src, left, top, size, size);
    }

    private Bitmap decodeImageFile(File file, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxSide
                && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void refreshPhotoPreview(final View sheet) {
        if (sheet == null) {
            return;
        }
        final ImageView preview = sheet.findViewById(R.id.icon_photo_preview);
        final TextView hint = sheet.findViewById(R.id.icon_photo_hint);
        if (preview == null || hint == null) {
            return;
        }
        final File file = appIconPhotoFile();
        if (!file.exists()) {
            preview.setPadding(dp(17), dp(17), dp(17), dp(17));
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setImageResource(R.drawable.ic_photo);
            hint.setText(R.string.icon_photo_empty);
            return;
        }
        hint.setText(R.string.icon_photo_ready);
        new Thread(() -> {
            final Bitmap bitmap = decodeImageFile(file, APP_ICON_MAX_SIDE);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || preview.getParent() == null) {
                    return;
                }
                if (bitmap == null) {
                    hint.setText(R.string.icon_photo_failed);
                    return;
                }
                preview.setPadding(0, 0, 0, 0);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setImageBitmap(bitmap);
            });
        }, "app-icon-preview").start();
    }

    /**
     * 把相册图片添加为桌面快捷方式。
     * Android 不允许第三方应用直接替换启动器图标，快捷方式是官方支持的等价做法：
     * 桌面上会多一个带照片图标、名称与本应用一致的入口，点开同样是本应用。
     */
    private void pinPhotoIconToHome(final View pinButton) {
        final File file = appIconPhotoFile();
        if (!file.exists()) {
            Toast.makeText(this, R.string.icon_pin_need_photo, Toast.LENGTH_SHORT).show();
            pickAppIconPhoto();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, R.string.icon_pin_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        final ShortcutManager manager =
                (ShortcutManager) getSystemService(Context.SHORTCUT_SERVICE);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(this, R.string.icon_pin_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pinButton != null) {
            pinButton.setEnabled(false);
        }
        new Thread(() -> {
            Bitmap bitmap = decodeImageFile(file, APP_ICON_MAX_SIDE);
            Bitmap square = centerCropSquare(bitmap);
            boolean updated = false;
            boolean requested = false;
            boolean failed = square == null;
            if (square != null) {
                try {
                    ShortcutInfo info = buildPhotoShortcut(square);
                    boolean exists = false;
                    for (ShortcutInfo item : manager.getShortcuts(ShortcutManager.FLAG_MATCH_PINNED)) {
                        if (SHORTCUT_ID_PHOTO_ICON.equals(item.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        manager.updateShortcuts(Collections.singletonList(info));
                        updated = true;
                    } else {
                        requested = manager.requestPinShortcut(info, null);
                    }
                } catch (Exception e) {
                    failed = true;
                }
            }
            final boolean done = updated;
            final boolean asked = requested;
            final boolean error = failed;
            runOnUiThread(() -> {
                if (pinButton != null) {
                    pinButton.setEnabled(true);
                }
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (done) {
                    Toast.makeText(this, R.string.icon_pin_updated, Toast.LENGTH_SHORT).show();
                } else if (asked) {
                    Toast.makeText(this, R.string.icon_pin_requested, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, error
                            ? R.string.icon_pin_failed : R.string.icon_pin_unsupported,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "app-icon-pin").start();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private ShortcutInfo buildPhotoShortcut(Bitmap bitmap) {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setClassName(this, MainActivity.class.getName());
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(this, SHORTCUT_ID_PHOTO_ICON)
                .setShortLabel(getString(R.string.app_name))
                .setLongLabel(getString(R.string.app_name))
                .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
                .setIntent(launch)
                .build();
    }

    private void updateToggle(View track, View thumb, boolean on, int margin) {
        int onRes = dsStyle ? R.drawable.bg_ds_toggle_on
                : (gptStyle ? R.drawable.bg_gpt_toggle_on : R.drawable.bg_toggle_on);
        track.setBackgroundResource(on ? onRes : R.drawable.bg_toggle_off);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        params.gravity = on ? (Gravity.CENTER_VERTICAL | Gravity.END)
                : (Gravity.CENTER_VERTICAL | Gravity.START);
        params.setMarginStart(on ? 0 : margin);
        params.setMarginEnd(on ? margin : 0);
        thumb.setLayoutParams(params);
    }

    private void applyNoteLink(TextView note) {
        String text = getString(R.string.fable_note);
        String link = getString(R.string.fable_note_link);
        int start = text.indexOf(link);
        if (start < 0) {
            note.setText(text);
            return;
        }
        SpannableString span = new SpannableString(text);
        int end = start + link.length();
        int linkColor = ContextCompat.getColor(this, dsStyle ? R.color.ds_brand
                : (gptStyle ? R.color.gpt_blue : R.color.link));
        span.setSpan(new ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                openSheet(SHEET_SETTINGS);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        note.setText(span);
        note.setMovementMethod(LinkMovementMethod.getInstance());
        note.setHighlightColor(Color.TRANSPARENT);
    }

    private void applyFullModel(String name) {
        fullSelected = name;
        prefs.setFullModel(name);
        String label = displayModelName(name, false) + " 高";
        homeModelView.setText(label);
        incognitoModelView.setText(label);
        if (chatModelView != null) {
            chatModelView.setText(label);
        }
        if (fullSheet != null) {
            updateFullChecks();
        }
    }

    private void updateFullChecks() {
        if (fullSheet == null) {
            return;
        }
        int[] checks = {R.id.full_check_fable, R.id.full_check_opus,
                R.id.full_check_sonnet, R.id.full_check_haiku};
        for (int i = 0; i < checks.length; i++) {
            View check = fullSheet.findViewById(checks[i]);
            if (check != null) {
                check.setVisibility(FULL_MODELS[i].equals(fullSelected)
                        ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void applyCompactModel(String name) {
        compactSelected = name;
        prefs.setCompactModel(name);
        voiceModelView.setText(displayModelName(name, true));
        if (compactSheet != null) {
            updateCompactChecks();
        }
    }

    private void updateCompactChecks() {
        if (compactSheet == null) {
            return;
        }
        int[] checks = {R.id.compact_check_fable, R.id.compact_check_sonnet,
                R.id.compact_check_haiku};
        for (int i = 0; i < checks.length; i++) {
            View check = compactSheet.findViewById(checks[i]);
            if (check != null) {
                check.setVisibility(COMPACT_MODELS[i].equals(compactSelected)
                        ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Attachments：添加到对话（相机 / 照片 / 文件）
    // ---------------------------------------------------------------------
    private void setupAttachmentViews() {
        homeAttachStrip = homeView.findViewById(R.id.home_attach_strip);
        chatAttachStrip = chatView.findViewById(R.id.chat_attach_strip);
        incognitoAttachStrip = incognitoView.findViewById(R.id.incognito_attach_strip);
        homeAttachList = homeView.findViewById(R.id.home_attach_list);
        chatAttachList = chatView.findViewById(R.id.chat_attach_list);
        incognitoAttachList = incognitoView.findViewById(R.id.incognito_attach_list);
        refreshAttachmentStrips();
    }

    /**
     * 三套皮肤的附件面板结构各不相同，这里按控件是否存在分别绑定：
     * Claude 是三卡片 + 项目/网页搜索/连接器列表；ChatGPT 是参考图的五行纯白小卡片；
     * DeepSeek 是三宫格 + 深度思考/智能搜索两个开关。
     */
    private void bindAttachSheet(View sheet) {
        View close = sheet.findViewById(R.id.attach_close);
        if (close != null) {
            close.setOnClickListener(v -> closeSheet());
        }

        bindAttachAction(sheet, R.id.attach_tile_camera, this::takePhoto);
        bindAttachAction(sheet, R.id.attach_tile_photo, this::pickPhoto);
        bindAttachAction(sheet, R.id.attach_tile_file, this::pickDocument);

        View project = sheet.findViewById(R.id.attach_row_project);
        if (project != null) {
            project.setOnClickListener(v -> Toast.makeText(this,
                    R.string.attach_project_todo, Toast.LENGTH_SHORT).show());
        }
        View connector = sheet.findViewById(R.id.attach_row_connector);
        if (connector != null) {
            connector.setOnClickListener(v -> Toast.makeText(this,
                    R.string.attach_connector_todo, Toast.LENGTH_SHORT).show());
        }

        final FrameLayout track = sheet.findViewById(R.id.attach_search_toggle);
        final View thumb = sheet.findViewById(R.id.attach_search_thumb);
        if (track != null && thumb != null) {
            applySearchToggle(track, thumb);
            track.setOnClickListener(v -> {
                webSearchOn = !webSearchOn;
                if (prefs != null) {
                    prefs.setSearchOn(webSearchOn);
                }
                applySearchToggle(track, thumb);
                applyDsPills();
                Toast.makeText(this, R.string.attach_web_toggle, Toast.LENGTH_SHORT).show();
            });
        }

        bindGptAttachSheet(sheet);
        bindDsAttachSheet(sheet);
    }

    /**
     * ChatGPT 附件面板：参考图里的白色悬浮小卡片。
     * 拍照 / 照片 / 文件复用通用绑定，“插件”为占位入口，“深入思考”与首页胶囊共用开关。
     */
    private void bindGptAttachSheet(View sheet) {
        if (!gptStyle) {
            return;
        }
        View plugin = sheet.findViewById(R.id.attach_row_plugin);
        if (plugin != null) {
            plugin.setOnClickListener(v -> Toast.makeText(this,
                    R.string.gpt_attach_plugin_todo, Toast.LENGTH_SHORT).show());
        }
        View think = sheet.findViewById(R.id.attach_row_think);
        if (think != null) {
            updateGptThinkRow(sheet);
            think.setOnClickListener(v -> {
                toggleThink(false);
                updateGptThinkRow(sheet);
                Toast.makeText(this, thinkOn ? R.string.gpt_think_tip_on
                        : R.string.gpt_think_tip_off, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** “深入思考”选中后把图标与文字染成品牌蓝，并显示右侧勾选。 */
    private void updateGptThinkRow(View sheet) {
        int color = ContextCompat.getColor(this,
                thinkOn ? R.color.gpt_blue : R.color.gpt_text);
        View check = sheet.findViewById(R.id.attach_think_check);
        if (check != null) {
            check.setVisibility(thinkOn ? View.VISIBLE : View.GONE);
        }
        ImageView icon = sheet.findViewById(R.id.attach_think_icon);
        if (icon != null) {
            icon.setColorFilter(color);
        }
        TextView text = sheet.findViewById(R.id.attach_think_text);
        if (text != null) {
            text.setTextColor(color);
        }
    }

    /** 附件入口统一“先收面板，再执行动作”，避免选择器返回时面板还压在页面上。 */
    private void bindAttachAction(View sheet, int id, Runnable action) {
        View tile = sheet.findViewById(id);
        if (tile == null) {
            return;
        }
        tile.setOnClickListener(v -> {
            closeSheet();
            action.run();
        });
    }

    /** DeepSeek 专属附件面板：深度思考 / 智能搜索两个真实开关。 */
    private void bindDsAttachSheet(View sheet) {
        View thinkRow = sheet.findViewById(R.id.attach_ds_think_row);
        View searchRow = sheet.findViewById(R.id.attach_ds_search_row);
        if (thinkRow == null && searchRow == null) {
            return;
        }
        updateDsAttachSwitches(sheet);
        if (thinkRow != null) {
            thinkRow.setOnClickListener(v -> {
                toggleThink();
                updateDsAttachSwitches(sheet);
            });
        }
        if (searchRow != null) {
            searchRow.setOnClickListener(v -> {
                toggleDsSearch();
                updateDsAttachSwitches(sheet);
            });
        }
    }

    private void updateDsAttachSwitches(View sheet) {
        int accent = ContextCompat.getColor(this, R.color.ds_brand);
        int normalIcon = ContextCompat.getColor(this, R.color.ds_icon);
        int normalText = ContextCompat.getColor(this, R.color.ds_text);
        ImageView thinkIcon = sheet.findViewById(R.id.attach_ds_think_icon);
        TextView thinkText = sheet.findViewById(R.id.attach_ds_think_text);
        ImageView searchIcon = sheet.findViewById(R.id.attach_ds_search_icon);
        TextView searchText = sheet.findViewById(R.id.attach_ds_search_text);
        if (thinkIcon != null) {
            thinkIcon.setColorFilter(thinkOn ? accent : normalIcon);
        }
        if (thinkText != null) {
            thinkText.setTextColor(thinkOn ? accent : normalText);
        }
        if (searchIcon != null) {
            searchIcon.setColorFilter(webSearchOn ? accent : normalIcon);
        }
        if (searchText != null) {
            searchText.setTextColor(webSearchOn ? accent : normalText);
        }
        applyAttachSwitch(sheet.findViewById(R.id.attach_ds_think_track),
                sheet.findViewById(R.id.attach_ds_think_thumb), thinkOn);
        applyAttachSwitch(sheet.findViewById(R.id.attach_ds_search_track),
                sheet.findViewById(R.id.attach_ds_search_thumb), webSearchOn);
    }

    private void applyAttachSwitch(FrameLayout track, View thumb, boolean on) {
        if (track == null || thumb == null) {
            return;
        }
        track.setBackgroundResource(on
                ? R.drawable.bg_ds_toggle_on : R.drawable.bg_toggle_off);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        params.gravity = on
                ? (Gravity.CENTER_VERTICAL | Gravity.END)
                : (Gravity.CENTER_VERTICAL | Gravity.START);
        params.setMarginStart(on ? 0 : dp(3));
        params.setMarginEnd(on ? dp(3) : 0);
        thumb.setLayoutParams(params);
    }

    private void applySearchToggle(FrameLayout track, View thumb) {
        int onRes = dsStyle ? R.drawable.bg_ds_toggle_on
                : (gptStyle ? R.drawable.bg_gpt_toggle_on : R.drawable.bg_toggle_blue);
        track.setBackgroundResource(webSearchOn ? onRes : R.drawable.bg_toggle_off);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        params.gravity = webSearchOn
                ? (Gravity.CENTER_VERTICAL | Gravity.END)
                : (Gravity.CENTER_VERTICAL | Gravity.START);
        params.setMarginStart(webSearchOn ? 0 : dp(3));
        params.setMarginEnd(webSearchOn ? dp(3) : 0);
        thumb.setLayoutParams(params);
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.attach_photo)), REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.attach_picker_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_DOCUMENT);
        } catch (Exception e) {
            // 个别 ROM 没有 DocumentsUI，退回兼容的 GET_CONTENT
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(Intent.createChooser(fallback,
                        getString(R.string.attach_file)), REQUEST_PICK_DOCUMENT);
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.attach_picker_missing,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** 相机拍下的照片先写入 cache，再压缩复制到应用私有目录，不依赖相册权限。 */
    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("camera dir");
            }
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraOutputFile = file;
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            if (cameraOutputFile != null) {
                cameraOutputFile.delete();
                cameraOutputFile = null;
            }
            Toast.makeText(this, R.string.attach_camera_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void leaveVoiceForAttachment() {
        if (currentScreen != SCREEN_VOICE) {
            return;
        }
        if (voiceReturnScreen == SCREEN_CHAT) {
            showChatScreen();
        } else if (voiceReturnScreen == SCREEN_INCOGNITO) {
            showIncognito();
        } else {
            showHome();
        }
    }

    private void addImageAttachment(final Uri uri, final String preferredName) {
        leaveVoiceForAttachment();
        if (!ensureCanAddAttachment()) {
            return;
        }
        final Attachment attachment = startImageAttachment(
                preferredName != null ? preferredName : queryFileName(uri), contentType(uri));
        new Thread(() -> {
            Bitmap bitmap = decodeImageFromUri(uri);
            boolean saved = saveImageAttachment(attachment, bitmap);
            if (bitmap != null) {
                bitmap.recycle();
            }
            completeImageAttachment(attachment, saved);
        }, "attach-image").start();
    }

    private void addImageAttachment(final File file, final String preferredName) {
        leaveVoiceForAttachment();
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.attach_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureCanAddAttachment()) {
            file.delete();
            return;
        }
        final Attachment attachment = startImageAttachment(
                preferredName != null ? preferredName : file.getName(), "image/jpeg");
        new Thread(() -> {
            Bitmap bitmap = decodeImageFromFile(file);
            boolean saved = saveImageAttachment(attachment, bitmap);
            if (bitmap != null) {
                bitmap.recycle();
            }
            file.delete();
            completeImageAttachment(attachment, saved);
        }, "attach-image").start();
    }

    private Attachment startImageAttachment(String name, String mime) {
        Attachment attachment = new Attachment();
        attachment.kind = "image";
        attachment.name = name == null || name.trim().isEmpty() ? "图片" : name;
        attachment.mime = mime == null || mime.isEmpty() ? "image/*" : mime;
        attachment.loading = true;
        pendingAttachments.add(attachment);
        refreshAttachmentStrips();
        return attachment;
    }

    private void completeImageAttachment(final Attachment attachment, final boolean saved) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            attachment.loading = false;
            if (!saved) {
                pendingAttachments.remove(attachment);
                Toast.makeText(this, R.string.attach_add_failed, Toast.LENGTH_SHORT).show();
            }
            refreshAttachmentStrips();
        });
    }

    private void addFileAttachment(final Uri uri) {
        leaveVoiceForAttachment();
        if (!ensureCanAddAttachment()) {
            return;
        }
        final Attachment attachment = new Attachment();
        attachment.kind = "file";
        attachment.name = queryFileName(uri);
        attachment.mime = contentType(uri);
        attachment.size = queryFileSize(uri);
        attachment.loading = true;
        pendingAttachments.add(attachment);
        refreshAttachmentStrips();

        final boolean textFile = isTextFile(attachment.name, attachment.mime);
        final boolean copyText = textFile
                && (attachment.size <= 0 || attachment.size <= MAX_TEXT_COPY_BYTES);
        new Thread(() -> {
            if (copyText) {
                File target = newAttachmentFile("doc", extensionOf(attachment.name));
                long copied = copyUriToFile(uri, target);
                if (copied >= 0) {
                    attachment.path = target.getAbsolutePath();
                    attachment.size = copied;
                    attachment.text = readTextFile(target);
                } else {
                    target.delete();
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                attachment.loading = false;
                refreshAttachmentStrips();
            });
        }, "attach-file").start();
    }

    private boolean hasLoadingAttachment() {
        for (Attachment attachment : pendingAttachments) {
            if (attachment.loading) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureCanAddAttachment() {
        if (pendingAttachments.size() >= MAX_ATTACHMENTS) {
            Toast.makeText(this, getString(R.string.attach_too_many, MAX_ATTACHMENTS),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private List<Attachment> takePendingAttachments() {
        List<Attachment> result = new ArrayList<>(pendingAttachments);
        pendingAttachments.clear();
        refreshAttachmentStrips();
        return result;
    }

    private void removeAttachment(Attachment attachment) {
        pendingAttachments.remove(attachment);
        refreshAttachmentStrips();
    }

    /** 首页、聊天页、无痕页共用同一组待发送附件，这里同步刷新三个预览条。 */
    private void refreshAttachmentStrips() {
        if (homeAttachList == null || chatAttachList == null || incognitoAttachList == null) {
            return;
        }
        LinearLayout[] lists = {homeAttachList, chatAttachList, incognitoAttachList};
        HorizontalScrollView[] strips = {homeAttachStrip, chatAttachStrip, incognitoAttachStrip};
        boolean hasAttachment = !pendingAttachments.isEmpty();
        for (int i = 0; i < lists.length; i++) {
            lists[i].removeAllViews();
            strips[i].setVisibility(hasAttachment ? View.VISIBLE : View.GONE);
            if (!hasAttachment) {
                continue;
            }
            for (Attachment attachment : pendingAttachments) {
                lists[i].addView(buildAttachmentCard(attachment, true));
            }
        }
        updateComposerActionStates();
        if (currentScreen == SCREEN_CHAT && chatScroll != null) {
            chatScroll.post(this::scrollChatToBottom);
        }
    }

    /** 有无待发送附件会改变发送按钮的状态，这里统一刷新三个输入区。 */
    private void updateComposerActionStates() {
        if (homeComposerAction != null) {
            updateSendButton(homeComposerAction,
                    homeInput != null && homeInput.getText().length() > 0);
        }
        if (incognitoComposerAction != null) {
            updateSendButton(incognitoComposerAction,
                    incognitoInput != null && incognitoInput.getText().length() > 0);
        }
        if (chatAction != null && chatInput != null) {
            updateChatAction();
        }
    }

    private void updateSendButton(ImageButton action, boolean hasText) {
        if (action == null) {
            return;
        }
        boolean active = hasText || !pendingAttachments.isEmpty();
        if (dsStyle) {
            // DeepSeek 输入卡：有内容时蓝色圆形向上箭头，空白时描边语音按钮。
            action.clearColorFilter();
            action.setBackgroundResource(active
                    ? R.drawable.bg_ds_composer_send
                    : R.drawable.bg_ds_composer_voice);
            action.setImageResource(active
                    ? R.drawable.ic_ds_arrow_up : R.drawable.ic_ds_composer_voice);
            return;
        }
        if (gptStyle) {
            // 首页 / 工作页的输入区与参考图一致：蓝底波形，输入后变成向上箭头。
            action.setImageResource(active
                    ? R.drawable.ic_gpt_arrow_up : R.drawable.ic_gpt_waveform);
            action.setBackgroundResource(R.drawable.bg_gpt_blue_circle);
            return;
        }
        action.setImageResource(active ? R.drawable.ic_send : R.drawable.ic_waveform);
        action.setBackgroundResource(active
                ? R.drawable.bg_send_orange : R.drawable.bg_black_circle);
    }

    private View buildAttachmentCard(final Attachment attachment, final boolean editable) {
        View card;
        if (attachment.isImage()) {
            card = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_attach_image,
                    R.layout.item_attach_image_gpt, R.layout.item_attach_image_ds),
                    null, false);
            ImageView thumb = card.findViewById(R.id.attach_item_thumb);
            loadThumbInto(thumb, attachment);
        } else {
            card = LayoutInflater.from(this).inflate(styleLayout(R.layout.item_attach_file,
                    R.layout.item_attach_file_gpt, R.layout.item_attach_file_ds),
                    null, false);
            TextView badge = card.findViewById(R.id.attach_item_badge);
            TextView name = card.findViewById(R.id.attach_item_name);
            TextView desc = card.findViewById(R.id.attach_item_desc);
            badge.setText(badgeFor(attachment.name));
            name.setText(attachment.name);
            if (attachment.loading) {
                desc.setText(R.string.attach_reading);
            } else {
                String size = formatSize(attachment.size);
                desc.setText(attachment.text != null ? size + " · 文本" : size);
            }
        }

        ImageView remove = card.findViewById(R.id.attach_item_remove);
        if (editable) {
            remove.setOnClickListener(v -> removeAttachment(attachment));
        } else {
            remove.setVisibility(View.GONE);
        }

        ImageView ring = card.findViewById(R.id.attach_item_loading);
        if (ring != null) {
            if (attachment.loading) {
                ring.setVisibility(View.VISIBLE);
                ring.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_loading));
            } else {
                ring.clearAnimation();
                ring.setVisibility(View.GONE);
            }
        }
        return card;
    }

    private void loadThumbInto(final ImageView view, final Attachment attachment) {
        if (view == null) {
            return;
        }
        final String path = attachment.path;
        view.setTag(path);
        view.setImageResource(R.drawable.ic_starburst);
        if (path == null) {
            return;
        }
        Bitmap cached;
        synchronized (thumbCache) {
            cached = thumbCache.get(path);
        }
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        new Thread(() -> {
            final Bitmap bitmap = decodeSampledFile(new File(path), THUMB_SIDE);
            if (bitmap == null) {
                return;
            }
            synchronized (thumbCache) {
                thumbCache.put(path, bitmap);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (path.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        }, "attach-thumb").start();
    }

    private Bitmap decodeSampledFile(File file, int targetSide) {
        if (file == null || !file.exists()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** 相册/相机图片按最长边采样，旋转到正方向后保存到私有目录，供预览和历史消息复用。 */
    private Bitmap decodeImageFromUri(Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(input);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = null;
        input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input != null) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception | OutOfMemoryError ignored) {
            // 由调用方提示失败
        } finally {
            closeQuietly(input);
        }
        if (bitmap == null) {
            return null;
        }
        int rotation = readExifRotation(uri);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }
        return bitmap;
    }

    private Bitmap decodeImageFromFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        Bitmap bitmap = decodeSampledFile(file, MAX_IMAGE_SIDE);
        if (bitmap == null) {
            return null;
        }
        int rotation = readExifRotation(Uri.fromFile(file));
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }
        return bitmap;
    }

    private boolean saveImageAttachment(Attachment attachment, Bitmap source) {
        if (source == null) {
            return false;
        }
        Bitmap scaled = source;
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide > MAX_IMAGE_SIDE) {
            float ratio = MAX_IMAGE_SIDE / (float) maxSide;
            int width = Math.max(1, Math.round(source.getWidth() * ratio));
            int height = Math.max(1, Math.round(source.getHeight() * ratio));
            try {
                scaled = Bitmap.createScaledBitmap(source, width, height, true);
            } catch (Exception | OutOfMemoryError e) {
                return false;
            }
        }
        File target = newAttachmentFile("img", "jpg");
        FileOutputStream output = null;
        Bitmap flattened = null;
        boolean saved = false;
        try {
            int width = scaled.getWidth();
            int height = scaled.getHeight();
            flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(scaled, 0f, 0f, new Paint(Paint.ANTI_ALIAS_FLAG));
            output = new FileOutputStream(target);
            saved = flattened.compress(Bitmap.CompressFormat.JPEG, 90, output);
            output.flush();
            if (saved) {
                attachment.path = target.getAbsolutePath();
                attachment.size = target.length();
                attachment.width = width;
                attachment.height = height;
                attachment.mime = "image/jpeg";
            }
        } catch (Exception | OutOfMemoryError e) {
            saved = false;
        } finally {
            closeQuietly(output);
            if (flattened != null) {
                flattened.recycle();
            }
            if (scaled != source) {
                scaled.recycle();
            }
            if (!saved) {
                target.delete();
            }
        }
        return saved;
    }

    private File attachmentsDir() {
        File dir = new File(getFilesDir(), "attachments");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File newAttachmentFile(String prefix, String extension) {
        String ext = extension == null || extension.trim().isEmpty()
                ? "bin" : extension.replaceAll("[^A-Za-z0-9]", "");
        if (ext.isEmpty()) {
            ext = "bin";
        }
        return new File(attachmentsDir(), prefix + "_" + System.currentTimeMillis()
                + "_" + (int) (Math.random() * 1000) + "." + ext);
    }

    private long copyUriToFile(Uri uri, File target) {
        InputStream input = null;
        FileOutputStream output = null;
        long total = 0;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return -1;
            }
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ATTACHMENT_COPY_BYTES) {
                    return -1;
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            return total;
        } catch (Exception e) {
            return -1;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (total > MAX_ATTACHMENT_COPY_BYTES) {
                target.delete();
            }
        }
    }

    private String readTextFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ATTACHMENT_BYTES) {
                    break;
                }
                output.write(buffer, 0, read);
            }
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            return text.indexOf('\u0000') >= 0 ? null : text;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(input);
        }
    }

    private static final String[] TEXT_EXTENSIONS = {
            "txt", "md", "markdown", "json", "csv", "xml", "log", "java", "kt", "kts",
            "py", "js", "ts", "tsx", "jsx", "html", "htm", "css", "scss", "yml", "yaml",
            "sh", "gradle", "properties", "ini", "sql", "c", "cpp", "h", "hpp", "go",
            "rs", "rb", "php", "swift", "toml", "conf", "gitignore", "env"
    };

    private boolean isTextFile(String name, String mime) {
        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (lowerMime.startsWith("text/") || lowerMime.contains("json")
                || lowerMime.contains("xml") || lowerMime.contains("javascript")
                || lowerMime.contains("x-sh") || lowerMime.contains("x-python")) {
            return true;
        }
        String ext = extensionOf(name);
        for (String candidate : TEXT_EXTENSIONS) {
            if (candidate.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String badgeFor(String name) {
        String ext = extensionOf(name);
        if (ext.isEmpty()) {
            return "FILE";
        }
        String upper = ext.toUpperCase(Locale.ROOT);
        return upper.length() > 4 ? upper.substring(0, 4) : upper;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) {
            return "大小未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String contentType(Uri uri) {
        try {
            String mime = getContentResolver().getType(uri);
            return mime == null ? "" : mime;
        } catch (Exception e) {
            return "";
        }
    }

    private long queryFileSize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
            // 某些 Provider 不返回大小，按未知处理
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1;
    }

    // ---- 附件在消息中的持久化与 API 文本拼装 ----

    private String encodeUserMessage(String text, List<Attachment> attachments) {
        String visible = text == null ? "" : text;
        if (attachments == null || attachments.isEmpty()) {
            return visible;
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : attachments) {
            try {
                JSONObject json = new JSONObject();
                json.put("kind", attachment.kind);
                json.put("name", attachment.name);
                json.put("mime", attachment.mime);
                json.put("size", attachment.size);
                json.put("width", attachment.width);
                json.put("height", attachment.height);
                json.put("path", attachment.path == null ? "" : attachment.path);
                builder.append(ATTACH_MARK).append(json.toString()).append('\n');
            } catch (Exception ignored) {
                // 单个附件编码失败不影响消息主体
            }
        }
        builder.append(visible);
        return builder.toString();
    }

    private List<Attachment> decodeAttachments(String content) {
        List<Attachment> result = new ArrayList<>();
        if (content == null) {
            return result;
        }
        int pos = 0;
        while (content.startsWith(ATTACH_MARK, pos)) {
            int end = content.indexOf('\n', pos);
            if (end < 0) {
                break;
            }
            String raw = content.substring(pos + ATTACH_MARK.length(), end);
            try {
                JSONObject json = new JSONObject(raw);
                Attachment attachment = new Attachment();
                attachment.kind = json.optString("kind", "file");
                attachment.name = json.optString("name", "附件");
                attachment.mime = json.optString("mime", "");
                attachment.size = json.optLong("size", -1);
                attachment.width = json.optInt("width", 0);
                attachment.height = json.optInt("height", 0);
                String path = json.optString("path", "");
                attachment.path = path.isEmpty() ? null : path;
                result.add(attachment);
            } catch (Exception e) {
                // 格式异常时停止解析，剩余内容按普通文本处理
                break;
            }
            pos = end + 1;
        }
        return result;
    }

    private String decodeVisibleText(String content) {
        if (content == null) {
            return "";
        }
        int pos = 0;
        while (content.startsWith(ATTACH_MARK, pos)) {
            int end = content.indexOf('\n', pos);
            if (end < 0) {
                break;
            }
            String raw = content.substring(pos + ATTACH_MARK.length(), end);
            try {
                new JSONObject(raw);
            } catch (Exception e) {
                break;
            }
            pos = end + 1;
        }
        return content.substring(pos);
    }

    /**
     * 挑选本轮请求要真正携带的图片：优先最近的消息，控制总字节数，
     * 避免多轮长对话把 base64 请求体撑到接口限制之外。
     */
    private java.util.Set<Attachment> selectImagesForApi(List<Turn> turns) {
        java.util.Set<Attachment> selected = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Attachment, Boolean>());
        int count = 0;
        long total = 0;
        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn turn = turns.get(i);
            if (!ChatStore.ROLE_USER.equals(turn.role) || turn.attachments.isEmpty()) {
                continue;
            }
            for (int j = turn.attachments.size() - 1; j >= 0; j--) {
                Attachment attachment = turn.attachments.get(j);
                if (!attachment.isImage() || attachment.path == null) {
                    continue;
                }
                long length = new File(attachment.path).length();
                if (length <= 0 || length > MAX_API_IMAGE_BYTES) {
                    continue;
                }
                if (count >= MAX_API_IMAGES || total + length > MAX_API_IMAGE_TOTAL_BYTES) {
                    continue;
                }
                selected.add(attachment);
                count++;
                total += length;
            }
        }
        return selected;
    }

    /** 把本地图片读成 DeepSeek 视觉接口要求的 data:image/...;base64,... 形式。 */
    private String imageDataUrl(Attachment attachment) {
        if (attachment == null || attachment.path == null) {
            return null;
        }
        File file = new File(attachment.path);
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_API_IMAGE_BYTES) {
            return null;
        }
        InputStream input = null;
        ByteArrayOutputStream output = null;
        try {
            input = new FileInputStream(file);
            output = new ByteArrayOutputStream((int) Math.min(file.length(), 2L * 1024 * 1024));
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            String mime = detectImageMime(bytes);
            return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    /** 按文件头判断图片真实格式（DeepSeek 以实际内容为准）。 */
    private static String detectImageMime(byte[] data) {
        if (data == null || data.length < 12) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    /**
     * 图片会编码为 base64 随消息发给 deepseek-flash 视觉接口；
     * 文本文件读取正文一起发送，二进制文件只发送元信息。
     */
    private String buildApiContent(String visibleText, List<Attachment> attachments,
                                   java.util.Set<Attachment> sentImages) {
        String visible = visibleText == null ? "" : visibleText;
        if (attachments == null || attachments.isEmpty()) {
            return visible;
        }
        StringBuilder builder = new StringBuilder();
        if (!visible.trim().isEmpty()) {
            builder.append(visible).append("\n\n");
        }
        for (Attachment attachment : attachments) {
            String name = attachment.name == null ? "附件" : attachment.name;
            if (attachment.isImage()) {
                String dimensions = attachment.width > 0 && attachment.height > 0
                        ? attachment.width + "×" + attachment.height : "尺寸未知";
                boolean sent = sentImages != null && sentImages.contains(attachment);
                builder.append(getString(sent ? R.string.attach_image_prompt
                                : R.string.attach_image_skipped, name,
                        dimensions, formatSize(attachment.size))).append('\n');
                continue;
            }
            String text = attachment.text;
            if (text == null && attachment.path != null) {
                text = readTextFile(new File(attachment.path));
            }
            if (text != null && !text.trim().isEmpty()) {
                if (text.length() > MAX_ATTACHMENT_PROMPT_CHARS) {
                    text = text.substring(0, MAX_ATTACHMENT_PROMPT_CHARS) + "\n…";
                }
                builder.append(getString(R.string.attach_text_prompt, name,
                        formatSize(attachment.size), text)).append('\n');
            } else {
                builder.append(getString(R.string.attach_file_prompt, name,
                        badgeFor(name), formatSize(attachment.size))).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String conversationTitle(String text, List<Attachment> attachments) {
        if (text != null && !text.trim().isEmpty()) {
            return shortTitle(text);
        }
        if (attachments != null && !attachments.isEmpty()) {
            Attachment first = attachments.get(0);
            String label = (first.isImage() ? "图片 · " : "文件 · ")
                    + (first.name == null ? "" : first.name);
            return shortTitle(label);
        }
        return getString(R.string.new_conversation);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_LOGO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handlePickedLogo(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_PICK_APP_ICON) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handlePickedAppIcon(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                addImageAttachment(data.getData(), null);
            }
            return;
        }
        if (requestCode == REQUEST_TAKE_PHOTO) {
            File file = cameraOutputFile;
            cameraOutputFile = null;
            if (resultCode == RESULT_OK && file != null && file.length() > 0) {
                addImageAttachment(file, "相机照片.jpg");
            } else if (file != null) {
                file.delete();
            }
            return;
        }
        if (requestCode == REQUEST_PICK_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                addFileAttachment(data.getData());
            }
        }
    }

    private String queryFileName(Uri uri) {
        String name = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        name = cursor.getString(index);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return name == null ? "文件" : name;
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            focus.clearFocus();
        }
    }

    /**
     * 发送消息后立即收起输入法面板。
     *
     * <p>只调用 hide 不清焦点时，部分 ROM 会在消息列表滚动/重绘后又把键盘顶起来，
     * 所以先把首页、会话页、无痕页三个输入框的焦点统一清掉再隐藏 IME。
     * 首页发送会切到会话页，窗口 token 变化可能让第一次 hide 失效，因此再补一次
     * 延迟隐藏；如果用户已经主动点回输入框，则保持键盘展开，不打断输入。</p>
     */
    private void dismissComposerKeyboard() {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        View focus = getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (focus != null) {
            focus.clearFocus();
        }
        if (homeInput != null) {
            homeInput.clearFocus();
        }
        if (chatInput != null) {
            chatInput.clearFocus();
        }
        if (incognitoInput != null) {
            incognitoInput.clearFocus();
        }
        if (imm != null) {
            if (focus != null && focus.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            if (decor != null && decor.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
            }
        }
        if (decor == null) {
            return;
        }
        handler.postDelayed(() -> {
            View current = getCurrentFocus();
            if (current instanceof EditText
                    && (current == homeInput || current == chatInput
                    || current == incognitoInput)) {
                // 用户已经点回输入框，保持键盘展开
                return;
            }
            if (current != null) {
                current.clearFocus();
            }
            InputMethodManager again = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (again != null && decor.getWindowToken() != null) {
                again.hideSoftInputFromWindow(decor.getWindowToken(), 0);
            }
            if (currentScreen == SCREEN_CHAT) {
                scrollChatToBottom();
            }
        }, 120);
    }

    /**
     * 给输入框补上点击即聚焦并主动唤起键盘的逻辑。
     * 部分 ROM 在 edge-to-edge 布局下点击 EditText 只拿到焦点但不弹输入法；
     * 发送消息后焦点还可能被消息列表的滚动逻辑抢走，因此这里在按下时就
     * 主动把焦点拉回输入框，抬起后再延时唤出键盘，保证第二条消息也能输入。
     */
    private void attachKeyboardOnTap(final EditText input) {
        if (input == null) {
            return;
        }
        // 不消费触摸事件，让 EditText 继续处理光标定位和文本选择
        input.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!input.isEnabled()) {
                    input.setEnabled(true);
                }
                if (!input.hasFocus()) {
                    input.requestFocus();
                }
                // 按下就补一次唤出键盘，避免事件被列表/输入法吞掉时只聚焦不弹键盘
                input.post(() -> showKeyboard(input));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                input.post(() -> showKeyboard(input));
            }
            return false;
        });
        input.setOnClickListener(v -> {
            if (!input.isEnabled()) {
                input.setEnabled(true);
            }
            input.requestFocus();
            input.post(() -> showKeyboard(input));
        });
    }

    private void restartInput(EditText input) {
        if (input == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(input);
        }
    }

    private void showKeyboard(final EditText input) {
        if (input == null || !input.isShown()) {
            return;
        }
        if (!input.hasFocus()) {
            input.requestFocus();
        }
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        boolean shown = imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        if (!shown) {
            // 窗口刚切换/刚从别的页面返回时第一次调用可能无效，下一帧再补一次
            input.postDelayed(() -> {
                if (input.isShown() && input.hasFocus()) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (gptNavController != null && gptNavController.dismissPopups()) {
            return;
        }
        if (sheetMode != SHEET_NONE) {
            closeSheet();
            return;
        }
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (currentScreen == SCREEN_VOICE) {
            closeVoice();
            return;
        }
        if (currentScreen == SCREEN_CHAT) {
            showHome();
            return;
        }
        if (currentScreen == SCREEN_INCOGNITO) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (client != null) {
            client.cancel();
        }
        if (chatMenuPopup != null) {
            chatMenuPopup.dismiss();
            chatMenuPopup = null;
        }
        if (gptNavController != null) {
            gptNavController.release();
            gptNavController = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (store != null) {
            store.close();
        }
        destroyDsActions(chatMessages);
        super.onDestroy();
    }

    /** 释放消息列表中所有 HTML 操作行 WebView，避免 Activity 退出后残留。 */
    private void destroyDsActions(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof DsActionsView) {
            ((DsActionsView) view).destroyQuietly();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                destroyDsActions(group.getChildAt(i));
            }
        }
    }
}
