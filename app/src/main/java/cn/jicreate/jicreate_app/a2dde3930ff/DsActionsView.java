package cn.jicreate.jicreate_app.a2dde3930ff;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 消息操作行的混合渲染容器（DeepSeek / ChatGPT 共用）。
 *
 * <p>这一排图标使用 assets 下的 HTML 在 WebView 中绘制，以便逐像素控制图标路径、
 * 尺寸、中心距、左右对齐与灰值；消息正文、气泡、侧边栏等其余部分仍为原生 View。
 * DeepSeek 加载 {@code ds_actions.html}，ChatGPT 由 {@link GptActionsView} 覆盖
 * {@link #pageAsset()} 加载 {@code gpt_actions.html}。HTML 采用像素稿中的动作名，
 * 由桥接层统一转换为应用现有的点击语义。</p>
 */
public class DsActionsView extends WebView {

    /** 点击回调，字符串取值为 copy / like / dislike / play / share / regen。 */
    public interface OnActionListener {
        void onAction(String action);
    }

    private static final int REACTION_NONE = 0;
    private static final int REACTION_LIKE = 1;
    private static final int REACTION_DISLIKE = 2;

    private OnActionListener actionListener;
    private boolean pageLoadStarted = false;
    private boolean pageReady = false;
    private boolean destroyed = false;
    private int reaction = REACTION_NONE;
    private boolean playing = false;

    public DsActionsView(Context context) {
        this(context, null);
    }

    public DsActionsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DsActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initWebView();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setLongClickable(false);
        setHapticFeedbackEnabled(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setOnLongClickListener(v -> true);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        // 页面与图标全部打包在 assets 内；保留 file 访问以兼容部分定制 WebView，
        // 同时通过 WebViewClient 拦截一切跳转，页面本身没有任何外部资源。
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        // 让 viewport meta 生效：1 CSS px 与 1dp 对齐，HTML 中的 33px/14px 即为 33dp/14dp。
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 应用固定白底，禁止系统强制深色把图标灰值反转。
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        addJavascriptInterface(new ActionBridge(), "AndroidActions");

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 只允许本地 assets 页面，避免误触跳转。
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                syncReaction();
                syncPlaying();
            }
        });
    }

    /**
     * 等 WebView 真正拿到宽度后再加载页面。
     * 若在构造时立即 loadUrl，页面可能在 viewport 宽度仍为 0 时完成布局，
     * 导致 device-width 与 dp 的 1:1 映射失效、图标挤在一起。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        loadPageIfNeeded(w);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(() -> loadPageIfNeeded(getWidth()));
    }

    private void loadPageIfNeeded(int width) {
        if (pageLoadStarted || destroyed || width <= 0) {
            return;
        }
        pageLoadStarted = true;
        loadUrl("file:///android_asset/" + pageAsset());
    }

    /**
     * 当前风格对应的图标页。ChatGPT 风格复用同一套混合渲染容器，
     * 只需覆盖本方法返回 {@code gpt_actions.html}。
     */
    protected String pageAsset() {
        return "ds_actions.html";
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    /** 0 = 无，1 = 点赞，2 = 点踩。 */
    public void setReaction(int value) {
        int next = (value == REACTION_LIKE || value == REACTION_DISLIKE) ? value : REACTION_NONE;
        if (reaction == next) {
            return;
        }
        reaction = next;
        syncReaction();
    }

    public int getReaction() {
        return reaction;
    }

    public void setPlaying(boolean value) {
        if (playing == value) {
            return;
        }
        playing = value;
        syncPlaying();
    }

    public boolean isPlaying() {
        return playing;
    }

    private void syncReaction() {
        if (!pageReady || destroyed) {
            return;
        }
        evaluateJavascript("window.DsActions&&window.DsActions.setReaction(" + reaction + ")", null);
    }

    private void syncPlaying() {
        if (!pageReady || destroyed) {
            return;
        }
        evaluateJavascript("window.DsActions&&window.DsActions.setPlaying(" + playing + ")", null);
    }

    /** WebView 被移出消息列表后主动释放，避免长会话里累积 WebView 实例。 */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroyQuietly();
    }

    public void destroyQuietly() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        try {
            stopLoading();
            clearHistory();
            removeJavascriptInterface("AndroidActions");
            destroy();
        } catch (Exception ignored) {
            // 某些 ROM 在窗口已销毁时调用 destroy 会抛异常，忽略即可。
        }
    }

    private final class ActionBridge {
        @JavascriptInterface
        public void onAction(final String action) {
            if (action == null) {
                return;
            }
            final String normalized = normalizeAction(action);
            post(() -> {
                if (destroyed || actionListener == null) {
                    return;
                }
                actionListener.onAction(normalized);
            });
        }
    }

    /** 将 HTML 像素稿的动作名映射到消息列表既有的动作名。 */
    private static String normalizeAction(String action) {
        switch (action) {
            case "up":
                return "like";
            case "down":
                return "dislike";
            case "vol":
                return "play";
            case "refresh":
                return "regen";
            default:
                return action;
        }
    }
}
