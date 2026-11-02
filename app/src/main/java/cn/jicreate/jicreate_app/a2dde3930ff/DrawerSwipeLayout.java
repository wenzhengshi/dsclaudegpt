package cn.jicreate.jicreate_app.a2dde3930ff;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import androidx.core.content.ContextCompat;

/**
 * 侧边栏手势容器。
 *
 * <p>三套皮肤共用同一个抽屉，但滑动手势刻意做了区分：
 * <ul>
 *   <li>Claude：不响应滑动，只能点菜单/关闭按钮/遮罩控制；</li>
 *   <li>ChatGPT：只支持屏幕左缘 24dp 内右滑打开，抽屉打开后在面板上左滑关闭，
 *       主内容区保持不动（标准抽屉）；</li>
 *   <li>DeepSeek：主界面任意位置右滑都能打开，面板左滑关闭；拖动过程中主内容区
 *       跟随手指同步右移（push 手感），松手按位移和速度吸附。</li>
 * </ul>
 */
public class DrawerSwipeLayout extends FrameLayout {

    /** Claude：不响应滑动。 */
    public static final int MODE_BUTTON_ONLY = 0;
    /** ChatGPT：仅屏幕左缘可滑开。 */
    public static final int MODE_EDGE = 1;
    /** DeepSeek：全屏可滑开，主内容区跟随位移。 */
    public static final int MODE_FOLLOW = 2;

    /** DeepSeek 打开时主内容区跟手右移的比例（相对抽屉宽度）。 */
    private static final float FOLLOW_RATIO = 0.10f;
    /** 松手后吸附到打开/关闭的进度阈值。 */
    private static final float SETTLE_THRESHOLD = 0.45f;
    /** 甩动速度阈值（px/s）。 */
    private static final float FLING_VELOCITY = 700f;
    private static final int OPEN_DURATION = 280;
    private static final int CLOSE_DURATION = 240;
    private static final int SETTLE_DURATION = 240;

    public interface OnDrawerListener {
        void onDrawerOpened();

        void onDrawerClosed();
    }

    private View drawerLayer;
    private View drawerPanel;
    private View drawerScrim;
    private View content;
    private int mode = MODE_BUTTON_ONLY;

    private int drawerWidth;
    private float progress;
    private boolean drawerOpen;
    private boolean sheetOpen;
    private ValueAnimator animator;
    private OnDrawerListener listener;

    private int touchSlop;
    private int edgeSize;
    private final Paint edgePaint = new Paint();

    private float downX;
    private float downY;
    private boolean tracking;
    private boolean dragging;
    private float startProgress;
    private VelocityTracker velocityTracker;

    public DrawerSwipeLayout(Context context) {
        this(context, null);
    }

    public DrawerSwipeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawerSwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        edgeSize = dp(24);
        edgePaint.setColor(ContextCompat.getColor(context, R.color.ds_border));
        setClipChildren(false);
        setClipToPadding(false);
    }

    /**
     * 绑定抽屉的四个视图。必须在布局 inflate 完成后调用。
     */
    public void bind(View layer, View panel, View scrim, View content, int mode) {
        this.drawerLayer = layer;
        this.drawerPanel = panel;
        this.drawerScrim = scrim;
        this.content = content;
        this.mode = mode;
        if (layer instanceof ViewGroup) {
            ((ViewGroup) layer).setClipChildren(false);
            ((ViewGroup) layer).setClipToPadding(false);
        }
        if (panel != null) {
            panel.setTranslationX(-100000f);
            if (mode == MODE_FOLLOW) {
                // DeepSeek 的侧边栏是一块可以整体推拉的面板，用投影强调它和主内容之间的层级。
                panel.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.ds_surface));
                panel.setElevation(dp(10));
            }
        }
        if (drawerLayer != null) {
            drawerLayer.setVisibility(GONE);
        }
    }

    public void setOnDrawerListener(OnDrawerListener listener) {
        this.listener = listener;
    }

    /** 与 Activity 的底部面板状态同步，避免在面板上滑动时误开抽屉。 */
    public void setSheetOpen(boolean open) {
        this.sheetOpen = open;
        if (open) {
            abortGesture();
        }
    }

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    public void setDrawerWidth(int width) {
        this.drawerWidth = width;
        if (drawerLayer != null && drawerLayer.getVisibility() == VISIBLE) {
            applyProgress(progress);
        }
    }

    public void open() {
        if (drawerLayer == null || drawerPanel == null) {
            return;
        }
        if (drawerOpen && progress >= 0.999f) {
            return;
        }
        drawerOpen = true;
        drawerLayer.setVisibility(VISIBLE);
        applyProgress(progress);
        if (listener != null) {
            listener.onDrawerOpened();
        }
        animateTo(true, OPEN_DURATION);
    }

    public void close() {
        if (drawerLayer == null || drawerPanel == null) {
            return;
        }
        if (!drawerOpen && progress <= 0.001f) {
            return;
        }
        drawerOpen = false;
        animateTo(false, CLOSE_DURATION);
    }

    private void animateTo(final boolean open, int duration) {
        if (animator != null) {
            // 先清空字段再 cancel，避免 onAnimationEnd 把抽屉提前置成旧目标状态。
            ValueAnimator old = animator;
            animator = null;
            old.cancel();
        }
        final float from = progress;
        final float to = open ? 1f : 0f;
        if (Math.abs(from - to) < 0.0001f) {
            applyProgress(to);
            finishState(open);
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(duration);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(animation -> applyProgress((float) animation.getAnimatedValue()));
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != animation) {
                    return;
                }
                animator = null;
                applyProgress(to);
                finishState(open);
            }
        });
        animator = va;
        va.start();
    }

    private void finishState(boolean open) {
        if (drawerLayer == null) {
            return;
        }
        if (open) {
            drawerLayer.setVisibility(VISIBLE);
            drawerOpen = true;
            if (listener != null) {
                listener.onDrawerOpened();
            }
        } else {
            drawerOpen = false;
            drawerLayer.setVisibility(GONE);
            if (content != null) {
                content.setTranslationX(0f);
            }
            if (drawerScrim != null) {
                drawerScrim.setAlpha(0f);
            }
            if (listener != null) {
                listener.onDrawerClosed();
            }
        }
    }

    private void applyProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        float width = effectiveWidth();
        if (width <= 0f) {
            return;
        }
        if (drawerLayer != null && progress > 0f
                && drawerLayer.getVisibility() != VISIBLE) {
            drawerLayer.setVisibility(VISIBLE);
        }
        if (drawerPanel != null) {
            drawerPanel.setTranslationX(-width + width * progress);
        }
        if (drawerScrim != null) {
            drawerScrim.setAlpha(progress);
        }
        // 只有 DeepSeek 风格让主界面跟着抽屉一起右移，形成与另两套皮肤不同的推拉感。
        if (content != null && mode == MODE_FOLLOW) {
            content.setTranslationX(width * FOLLOW_RATIO * progress);
        }
    }

    private float effectiveWidth() {
        if (drawerWidth > 0) {
            return drawerWidth;
        }
        return drawerPanel != null ? drawerPanel.getWidth() : 0f;
    }

    /**
     * DeepSeek 参考图里侧边栏与内容区之间有一条 1px 的浅灰分界线（实测 x=598/767≈78%），
     * 拖动过程中随面板一起移动；另两套皮肤没有这条线。
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mode != MODE_FOLLOW || sheetOpen || drawerLayer == null || drawerPanel == null
                || drawerLayer.getVisibility() != VISIBLE || progress <= 0f) {
            return;
        }
        float right = drawerPanel.getX() + drawerPanel.getWidth();
        edgePaint.setAlpha((int) (255 * progress));
        canvas.drawRect(right - 1f, drawerPanel.getTop(), right,
                drawerPanel.getBottom(), edgePaint);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (drawerLayer == null || mode == MODE_BUTTON_ONLY || sheetOpen) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTracking(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                if (!tracking || dragging) {
                    return dragging;
                }
                return tryStartDrag(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                releaseVelocityTracker();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (drawerLayer == null || mode == MODE_BUTTON_ONLY || sheetOpen) {
                return false;
            }
            // 页面空白处没有子 View 消费 DOWN 时，由容器自己接管，保证全屏都能滑开抽屉。
            beginTracking(ev);
            return tracking;
        }
        if (!tracking && !dragging) {
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                if (!dragging && !tryStartDrag(ev)) {
                    return true;
                }
                float width = effectiveWidth();
                if (width <= 0f) {
                    return true;
                }
                float dx = ev.getX() - downX;
                // 扣除触发拦截时的 slop，避免面板出现一次小跳变。
                dx -= Math.signum(dx) * touchSlop;
                applyProgress(startProgress + dx / width);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) {
                    tracking = false;
                    releaseVelocityTracker();
                    return true;
                }
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                    velocityTracker.computeCurrentVelocity(1000);
                }
                float vx = velocityTracker != null ? velocityTracker.getXVelocity() : 0f;
                boolean open;
                if (vx > FLING_VELOCITY) {
                    open = true;
                } else if (vx < -FLING_VELOCITY) {
                    open = false;
                } else {
                    open = progress > SETTLE_THRESHOLD;
                }
                dragging = false;
                tracking = false;
                releaseVelocityTracker();
                // 手势一松手就同步逻辑状态，让返回键/菜单按钮在吸附动画期间也能正确响应。
                drawerOpen = open;
                if (listener != null) {
                    if (open) {
                        listener.onDrawerOpened();
                    } else {
                        listener.onDrawerClosed();
                    }
                }
                animateTo(open, SETTLE_DURATION);
                return true;
            }
            default:
                return true;
        }
    }

    private void beginTracking(MotionEvent ev) {
        tracking = false;
        dragging = false;
        downX = ev.getX();
        downY = ev.getY();
        releaseVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);
        tracking = canStartGesture(ev);
    }

    /**
     * 判断当前事件是否已经构成横向拖拽手势；只有横向位移明显大于纵向位移时才接管，
     * 否则把事件留给列表的纵向滚动。
     */
    private boolean tryStartDrag(MotionEvent ev) {
        if (!tracking || dragging) {
            return dragging;
        }
        float dx = ev.getX() - downX;
        float dy = ev.getY() - downY;
        if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
            return false;
        }
        if (Math.abs(dy) > Math.abs(dx)) {
            tracking = false;
            return false;
        }
        // 完全关闭时只接管右滑手势，避免拦下页面里的左滑/系统返回手势；
        // 完全打开时只接管左滑，右滑仍交给子控件。
        if (progress <= 0.001f && dx < 0) {
            tracking = false;
            return false;
        }
        if (drawerOpen && progress >= 0.999f && dx > 0) {
            tracking = false;
            return false;
        }
        if (Math.abs(dx) > touchSlop * 1.2f
                && Math.abs(dx) > Math.abs(dy) * 1.15f) {
            // 真正开始拖动时才打断进行中的开合动画，轻点不会让抽屉停在半开状态。
            if (animator != null) {
                ValueAnimator old = animator;
                animator = null;
                old.cancel();
            }
            startProgress = progress;
            dragging = true;
            return true;
        }
        tracking = false;
        return false;
    }

    private boolean canStartGesture(MotionEvent ev) {
        if (drawerOpen || progress > 0.001f) {
            // 打开后可以从面板或遮罩处左滑关闭；落在输入框上的横向滑动仍留给文本选择，
            // 遮罩上的单击也继续走原有的点击关闭逻辑。
            return !isInsideEditable(ev);
        }
        if (mode == MODE_EDGE) {
            return ev.getRawX() <= edgeSize;
        }
        // DeepSeek：全屏可滑开，但避开输入框（可能在做文本选择）和已有横向滚动的内容。
        return !isInsideEditable(ev) && !isInsideHorizontalScroller(ev);
    }

    private boolean isInsideEditable(MotionEvent ev) {
        return containsViewAt(this, ev.getRawX(), ev.getRawY(), EditText.class);
    }

    private boolean isInsideHorizontalScroller(MotionEvent ev) {
        return containsViewAt(this, ev.getRawX(), ev.getRawY(), HorizontalScrollView.class);
    }

    /** 在可见视图树里查找触点是否落在某类控件上。 */
    private boolean containsViewAt(View view, float rawX, float rawY, Class<?> type) {
        if (view == null || view.getVisibility() != VISIBLE || !view.isShown()) {
            return false;
        }
        if (type.isInstance(view)) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            if (rawX >= location[0] && rawX < location[0] + view.getWidth()
                    && rawY >= location[1] && rawY < location[1] + view.getHeight()) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                if (containsViewAt(group.getChildAt(i), rawX, rawY, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void abortGesture() {
        tracking = false;
        dragging = false;
        releaseVelocityTracker();
    }

    private void releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            ValueAnimator old = animator;
            animator = null;
            old.cancel();
        }
        releaseVelocityTracker();
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
