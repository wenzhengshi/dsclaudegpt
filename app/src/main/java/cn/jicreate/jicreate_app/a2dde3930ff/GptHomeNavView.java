package cn.jicreate.jicreate_app.a2dde3930ff;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * 空对话状态下的 ChatGPT 标题栏。
 *
 * <p>严格按本轮参考稿（1080 设计宽度）等比绘制：
 * 栏高 193、左右外边距 39、顶边距 42；
 * 左侧 124 圆形菜单按钮、右侧 124 圆形消息按钮；
 * 中间 484×124 的“聊天 / 工作”分段控件，内边距 11、白色滑块 231×102，
 * 标签字号 40、字重 360、横向 0.935 压缩、纵向 -0.185 偏移。</p>
 *
 * <p>全部图形由 vector 与代码绘制，不依赖任何位图资源。</p>
 */
public class GptHomeNavView extends ViewGroup {

    public interface Listener {
        void onMenuClick();

        void onTabSelected(boolean chatTab);

        void onMessageClick();
    }

    public static final int TAB_CHAT = 0;
    public static final int TAB_WORK = 1;

    private static final float DESIGN_WIDTH = 1080f;
    private static final float BAR_HEIGHT = 193f;
    private static final float SIDE = 39f;
    private static final float TOP = 42f;
    private static final float BUTTON = 124f;
    private static final float SEG_WIDTH = 484f;
    private static final float SEG_HEIGHT = 124f;

    private static final int INK = 0xFF0D0D0D;

    private final CircleButton menuButton;
    private final CircleButton messageButton;
    private final SegmentedStrip segmented;
    private final Paint navPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient navGradient;

    private float unit = 1f;
    private int activeTab = TAB_CHAT;
    private Listener listener;

    public GptHomeNavView(Context context) {
        this(context, null);
    }

    public GptHomeNavView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GptHomeNavView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        menuButton = new CircleButton(context, R.drawable.ic_gpt_home_menu,
                51f, 28f, context.getString(R.string.cd_gpt_home_menu));
        menuButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenuClick();
            }
        });
        addView(menuButton);

        messageButton = new CircleButton(context, R.drawable.ic_gpt_home_msg,
                56f, 53f, context.getString(R.string.cd_gpt_home_messages));
        messageButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMessageClick();
            }
        });
        addView(messageButton);

        segmented = new SegmentedStrip(context);
        segmented.setContentDescription(context.getString(R.string.cd_gpt_home_tabs));
        segmented.setTabListener(chatTab -> {
            activeTab = chatTab ? TAB_CHAT : TAB_WORK;
            if (listener != null) {
                listener.onTabSelected(chatTab);
            }
        });
        addView(segmented);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 外部同步选中态；用户点击时由分段控件自行完成滑块动画。 */
    public void setActiveTab(boolean chatTab) {
        int tab = chatTab ? TAB_CHAT : TAB_WORK;
        if (tab == activeTab) {
            return;
        }
        activeTab = tab;
        segmented.setActive(tab, true);
    }

    public boolean isChatTab() {
        return activeTab == TAB_CHAT;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        float u = width / DESIGN_WIDTH;
        int height = Math.round(BAR_HEIGHT * u);
        setMeasuredDimension(width, Math.max(height, 1));

        int button = Math.round(BUTTON * u);
        menuButton.measure(MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY));
        messageButton.measure(MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY));
        segmented.measure(
                MeasureSpec.makeMeasureSpec(Math.round(SEG_WIDTH * u), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(SEG_HEIGHT * u), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) {
            return;
        }
        unit = w / DESIGN_WIDTH;
        segmented.updateMetrics(unit);
        segmented.setActive(activeTab, false);
        if (navGradient == null || oldw != w || oldh != h) {
            navGradient = new LinearGradient(0f, 0f, 0f, Math.max(h, 1),
                    new int[]{0x2EFFFFFF, 0x0AFFFFFF, 0x00FFFFFF, 0x1AFFFFFF},
                    new float[]{0f, 0.46f, 0.66f, 1f},
                    Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (unit <= 0f) {
            return;
        }
        int top = Math.round(TOP * unit);
        int size = Math.round(BUTTON * unit);
        int side = Math.round(SIDE * unit);

        menuButton.layout(side, top, side + size, top + size);

        int segWidth = Math.round(SEG_WIDTH * unit);
        int segLeft = (getWidth() - segWidth) / 2;
        segmented.layout(segLeft, top, segLeft + segWidth, top + size);

        int messageLeft = getWidth() - side - size;
        messageButton.layout(messageLeft, top, messageLeft + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (navGradient != null) {
            navPaint.setShader(navGradient);
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), navPaint);
            navPaint.setShader(null);
        }
    }

    // ---------------------------------------------------------------------
    // 左右圆形按钮：白圆 + 参考稿原始尺寸的图标
    // ---------------------------------------------------------------------
    private static final class CircleButton extends View {

        private final Drawable icon;
        private final int iconWidth;
        private final int iconHeight;
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CircleButton(Context context, int iconRes, float designWidth, float designHeight,
                     String contentDescription) {
            super(context);
            icon = ContextCompat.getDrawable(context, iconRes);
            iconWidth = Math.round(designWidth);
            iconHeight = Math.round(designHeight);
            circlePaint.setColor(0xFFFFFFFF);
            setContentDescription(contentDescription);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float scale = isPressed() ? 0.94f : 1f;
            canvas.save();
            canvas.scale(scale, scale, w / 2f, h / 2f);
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, circlePaint);
            if (icon != null) {
                float u = w / BUTTON;
                int iw = Math.round(iconWidth * u);
                int ih = Math.round(iconHeight * u);
                int left = (w - iw) / 2;
                int top = (h - ih) / 2;
                icon.setBounds(left, top, left + iw, top + ih);
                icon.draw(canvas);
            }
            canvas.restore();
        }
    }

    // ---------------------------------------------------------------------
    // 聊天 / 工作分段控件
    // ---------------------------------------------------------------------
    private static final class SegmentedStrip extends FrameLayout {

        interface TabListener {
            void onTabSelected(boolean chatTab);
        }

        private static final float STRIP_WIDTH = 484f;
        private static final float STRIP_HEIGHT = 124f;
        private static final float PAD = 11f;
        private static final float THUMB_WIDTH = 231f;
        private static final float THUMB_HEIGHT = 102f;
        private static final float FONT = 40f;
        /** 参考稿 .tab-label 的 translate(0, -0.185cqw)，即 -2/1080 设计像素。 */
        private static final float LABEL_OFFSET_Y = -2f;

        private final View thumb;
        private final TextView chatTab;
        private final TextView workTab;
        private final PathInterpolator thumbInterpolator =
                new PathInterpolator(0.34f, 1.2f, 0.36f, 1f);

        private TabListener tabListener;
        private ValueAnimator thumbAnimator;
        private float metricsUnit = -1f;
        private int active = TAB_CHAT;

        SegmentedStrip(Context context) {
            super(context);
            setBackgroundResource(R.drawable.bg_gpt_home_segment_track);
            setClipChildren(false);
            setClipToPadding(false);

            thumb = new View(context);
            thumb.setBackgroundResource(R.drawable.bg_gpt_home_segment_thumb);
            thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(thumb);

            chatTab = createTab(context, R.string.gpt_tab_chat);
            chatTab.setOnClickListener(v -> select(TAB_CHAT, true));
            addView(chatTab);

            workTab = createTab(context, R.string.gpt_tab_work);
            workTab.setOnClickListener(v -> select(TAB_WORK, true));
            addView(workTab);

            updateSelected();
        }

        private static TextView createTab(Context context, int textRes) {
            TextView tab = new TextView(context);
            tab.setText(textRes);
            tab.setGravity(Gravity.CENTER);
            tab.setIncludeFontPadding(false);
            tab.setSingleLine(true);
            tab.setTextColor(INK);
            tab.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tab.setLetterSpacing(0f);
            tab.setScaleX(0.935f);
            tab.setBackground(null);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.62f);
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                }
                return false;
            });
            return tab;
        }

        void setTabListener(TabListener tabListener) {
            this.tabListener = tabListener;
        }

        private void select(int tab, boolean animate) {
            setActive(tab, animate);
            if (tabListener != null) {
                tabListener.onTabSelected(tab == TAB_CHAT);
            }
        }

        void setActive(int tab, boolean animate) {
            active = tab;
            updateSelected();
            if (getWidth() <= 0) {
                return;
            }
            float target = tab * THUMB_WIDTH * (getWidth() / STRIP_WIDTH);
            if (thumbAnimator != null) {
                thumbAnimator.cancel();
                thumbAnimator = null;
            }
            if (!animate) {
                thumb.setTranslationX(target);
                return;
            }
            if (Math.abs(thumb.getTranslationX() - target) < 0.5f) {
                return;
            }
            thumbAnimator = ValueAnimator.ofFloat(thumb.getTranslationX(), target);
            thumbAnimator.setDuration(320);
            thumbAnimator.setInterpolator(thumbInterpolator);
            thumbAnimator.addUpdateListener(
                    animation -> thumb.setTranslationX((float) animation.getAnimatedValue()));
            thumbAnimator.start();
        }

        private void updateSelected() {
            chatTab.setSelected(active == TAB_CHAT);
            workTab.setSelected(active == TAB_WORK);
            chatTab.setContentDescription(getContext().getString(R.string.gpt_tab_chat)
                    + (active == TAB_CHAT ? "，已选中" : ""));
            workTab.setContentDescription(getContext().getString(R.string.gpt_tab_work)
                    + (active == TAB_WORK ? "，已选中" : ""));
        }

        void updateMetrics(float unit) {
            if (Math.abs(unit - metricsUnit) < 0.0001f) {
                return;
            }
            metricsUnit = unit;
            int textSize = Math.round(FONT * unit);
            chatTab.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            workTab.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            float offset = LABEL_OFFSET_Y * unit;
            chatTab.setTranslationY(offset);
            workTab.setTranslationY(offset);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
            float u = width / STRIP_WIDTH;
            int thumbW = Math.round(THUMB_WIDTH * u);
            int thumbH = Math.round(THUMB_HEIGHT * u);
            int exactW = MeasureSpec.makeMeasureSpec(thumbW, MeasureSpec.EXACTLY);
            int exactH = MeasureSpec.makeMeasureSpec(thumbH, MeasureSpec.EXACTLY);
            thumb.measure(exactW, exactH);
            chatTab.measure(exactW, exactH);
            workTab.measure(exactW, exactH);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            if (width <= 0) {
                return;
            }
            float u = width / STRIP_WIDTH;
            int pad = Math.round(PAD * u);
            int thumbW = Math.round(THUMB_WIDTH * u);
            int thumbH = Math.round(THUMB_HEIGHT * u);
            int thumbTop = Math.round((STRIP_HEIGHT - THUMB_HEIGHT) / 2f * u);
            thumb.layout(pad, thumbTop, pad + thumbW, thumbTop + thumbH);
            chatTab.layout(pad, thumbTop, pad + thumbW, thumbTop + thumbH);
            workTab.layout(pad + thumbW, thumbTop, pad + thumbW * 2, thumbTop + thumbH);
        }
    }
}
