package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.PathParser;

/**
 * ChatGPT 会话加载时的顶部标题栏。
 *
 * <p>参考稿以 1080 为设计宽度：容器高 193、左侧 124×124 圆形菜单按钮、
 * 右侧 254×124 白色胶囊（左半是「开启新对话」铅笔按钮，右半是更多操作）。
 * 所有尺寸按控件实际宽度 / 1080 等比换算，避免不同屏宽下比例漂移。</p>
 *
 * <p>图标与背景全部使用 vector / 代码绘制，不依赖任何位图资源。</p>
 */
public class GptChatNavBarView extends View {

    public interface Listener {
        void onMenuClick();

        /** 顶部铅笔按钮：开启一段新对话，不是编辑模式开关。 */
        void onNewChatClick();

        void onMoreClick();
    }

    private static final float DESIGN_WIDTH = 1080f;
    private static final float BAR_HEIGHT = 193f;

    private static final float MENU_LEFT = 39f;
    private static final float MENU_TOP = 37f;
    private static final float MENU_SIZE = 124f;
    private static final float MENU_ICON_LEFT = 37f;
    private static final float MENU_ICON_TOP = 48f;
    private static final float MENU_ICON_WIDTH = 50f;
    private static final float MENU_ICON_HEIGHT = 28f;

    private static final float CAPSULE_RIGHT = 39f;
    private static final float CAPSULE_TOP = 37f;
    private static final float CAPSULE_WIDTH = 254f;
    private static final float CAPSULE_HEIGHT = 124f;

    private static final float NEW_CHAT_ICON_LEFT = 37f;
    private static final float NEW_CHAT_ICON_TOP = 36f;

    private static final float MORE_ICON_RIGHT = 56f;
    private static final float MORE_ICON_TOP = 37f;
    private static final float MORE_ICON_WIDTH = 12f;
    private static final float MORE_ICON_HEIGHT = 50f;

    private static final int TARGET_NONE = 0;
    private static final int TARGET_MENU = 1;
    private static final int TARGET_NEW_CHAT = 2;
    private static final int TARGET_MORE = 3;

    private static final int COLOR_PAPER = 0xFFFFFFFF;
    private static final int COLOR_HOVER = 0xFFFBFBFB;
    private static final int COLOR_PRESSED = 0xFFF5F5F5;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path capsulePath = new Path();
    private final RectF capsuleRect = new RectF();
    private final Path editFramePath;
    private final Path editTipPath = new Path();
    private final Path editPencilPath;
    private final Drawable menuIcon;
    private final Drawable moreIcon;

    private Listener listener;
    private int pressedTarget = TARGET_NONE;

    private float unit = 1f;
    private float menuLeft;
    private float menuTop;
    private float menuSize;
    private float capsuleLeft;
    private float capsuleTop;
    private float capsuleWidth;
    private float capsuleHeight;

    public GptChatNavBarView(Context context) {
        this(context, null);
    }

    public GptChatNavBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GptChatNavBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackground(new GptNavBackdropDrawable());
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.gpt_nav_top));
        menuIcon = ContextCompat.getDrawable(context, R.drawable.ic_gpt_nav_menu);
        moreIcon = ContextCompat.getDrawable(context, R.drawable.ic_gpt_nav_more);
        iconPaint.setColor(0xFF0D0D0D);
        editFramePath = PathParser.createPathFromPathData(
                "M18.2 3.2H9.9A7.8 7.8 0 0 0 2.5 11V41.4A6.9 6.9 0 0 0 9.4 48.3H40.7"
                        + "A6.9 6.9 0 0 0 47.6 41.4V30.4");
        editPencilPath = PathParser.createPathFromPathData(
                "M41 0A9 9 0 0 1 50 9C50 10.9 49.6 12.7 48.6 14.2L34.3 29.3"
                        + "C30.5 33.3 21 37.1 14 36.5L14 30C17 21 25 12 32.8 6.2"
                        + "C34.6 4.4 37.7 0 41 0ZM41 5A4.4 4.4 0 0 1 45.4 9.4"
                        + "L25.2 29.9L20.1 30.4C23 21.5 31.5 14.5 36.5 9.7"
                        + "A4.4 4.4 0 0 1 41 5Z");
        if (editPencilPath != null) {
            editPencilPath.setFillType(Path.FillType.EVEN_ODD);
        }
        editTipPath.addCircle(17.6f, 32.3f, 4f, Path.Direction.CW);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int height = Math.round(BAR_HEIGHT * width / DESIGN_WIDTH);
        setMeasuredDimension(width, Math.max(height, 1));
    }

    private void updateGeometry() {
        unit = getWidth() / DESIGN_WIDTH;
        menuLeft = MENU_LEFT * unit;
        menuTop = MENU_TOP * unit;
        menuSize = MENU_SIZE * unit;
        capsuleWidth = CAPSULE_WIDTH * unit;
        capsuleHeight = CAPSULE_HEIGHT * unit;
        capsuleLeft = getWidth() - CAPSULE_RIGHT * unit - capsuleWidth;
        capsuleTop = CAPSULE_TOP * unit;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateGeometry();
        if (unit <= 0f) {
            return;
        }

        // 左侧圆形按钮：参考稿按下时略微缩放到 0.97。
        float menuScale = pressedTarget == TARGET_MENU ? 0.97f : 1f;
        float menuCenterX = menuLeft + menuSize / 2f;
        float menuCenterY = menuTop + menuSize / 2f;
        canvas.save();
        if (menuScale != 1f) {
            canvas.scale(menuScale, menuScale, menuCenterX, menuCenterY);
        }
        fillPaint.setColor(pressedTarget == TARGET_MENU ? COLOR_HOVER : COLOR_PAPER);
        canvas.drawCircle(menuCenterX, menuCenterY, menuSize / 2f, fillPaint);
        drawIcon(canvas, menuIcon,
                menuLeft + MENU_ICON_LEFT * unit,
                menuTop + MENU_ICON_TOP * unit,
                MENU_ICON_WIDTH * unit,
                MENU_ICON_HEIGHT * unit);
        canvas.restore();

        // 右侧白色胶囊。
        capsuleRect.set(capsuleLeft, capsuleTop,
                capsuleLeft + capsuleWidth, capsuleTop + capsuleHeight);
        fillPaint.setColor(COLOR_PAPER);
        canvas.drawRoundRect(capsuleRect, capsuleHeight / 2f, capsuleHeight / 2f, fillPaint);

        canvas.save();
        capsulePath.reset();
        capsulePath.addRoundRect(capsuleRect, capsuleHeight / 2f, capsuleHeight / 2f,
                Path.Direction.CW);
        canvas.clipPath(capsulePath);

        if (pressedTarget == TARGET_NEW_CHAT) {
            fillPaint.setColor(COLOR_PRESSED);
            canvas.drawRect(capsuleLeft, capsuleTop,
                    capsuleLeft + capsuleWidth / 2f, capsuleTop + capsuleHeight, fillPaint);
        }
        if (pressedTarget == TARGET_MORE) {
            fillPaint.setColor(COLOR_PRESSED);
            canvas.drawRect(capsuleLeft + capsuleWidth / 2f, capsuleTop,
                    capsuleLeft + capsuleWidth, capsuleTop + capsuleHeight, fillPaint);
        }
        canvas.restore();

        drawNewChatIcon(canvas,
                capsuleLeft + NEW_CHAT_ICON_LEFT * unit,
                capsuleTop + NEW_CHAT_ICON_TOP * unit, unit);
        drawIcon(canvas, moreIcon,
                capsuleLeft + capsuleWidth - MORE_ICON_RIGHT * unit - MORE_ICON_WIDTH * unit,
                capsuleTop + MORE_ICON_TOP * unit,
                MORE_ICON_WIDTH * unit,
                MORE_ICON_HEIGHT * unit);
    }

    /** 新对话图标按参考稿 51×51 的原始路径绘制，保证铅笔的镂空与笔尖形状。 */
    private void drawNewChatIcon(Canvas canvas, float left, float top, float scale) {
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        if (editFramePath != null) {
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(5.5f);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(editFramePath, iconPaint);
        }
        iconPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(editTipPath, iconPaint);
        if (editPencilPath != null) {
            canvas.drawPath(editPencilPath, iconPaint);
        }
        canvas.restore();
    }

    private void drawIcon(Canvas canvas, Drawable drawable,
                          float left, float top, float width, float height) {
        if (drawable == null) {
            return;
        }
        drawable.setBounds(Math.round(left), Math.round(top),
                Math.round(left + width), Math.round(top + height));
        drawable.draw(canvas);
    }

    private int hitTest(float x, float y) {
        float centerX = menuLeft + menuSize / 2f;
        float centerY = menuTop + menuSize / 2f;
        float radius = menuSize / 2f;
        float dx = x - centerX;
        float dy = y - centerY;
        if (dx * dx + dy * dy <= radius * radius) {
            return TARGET_MENU;
        }
        if (x >= capsuleLeft && x <= capsuleLeft + capsuleWidth
                && y >= capsuleTop && y <= capsuleTop + capsuleHeight) {
            return x < capsuleLeft + capsuleWidth / 2f ? TARGET_NEW_CHAT : TARGET_MORE;
        }
        return TARGET_NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                updateGeometry();
                int target = hitTest(event.getX(), event.getY());
                pressedTarget = target;
                invalidate();
                return target != TARGET_NONE;
            }
            case MotionEvent.ACTION_MOVE: {
                int target = hitTest(event.getX(), event.getY());
                if (target != pressedTarget) {
                    pressedTarget = target;
                    invalidate();
                }
                return pressedTarget != TARGET_NONE;
            }
            case MotionEvent.ACTION_UP: {
                int target = hitTest(event.getX(), event.getY());
                pressedTarget = TARGET_NONE;
                invalidate();
                if (target != TARGET_NONE) {
                    performClick();
                    dispatchTarget(target);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                pressedTarget = TARGET_NONE;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void dispatchTarget(int target) {
        if (listener == null) {
            return;
        }
        if (target == TARGET_MENU) {
            listener.onMenuClick();
        } else if (target == TARGET_NEW_CHAT) {
            listener.onNewChatClick();
        } else if (target == TARGET_MORE) {
            listener.onMoreClick();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
