package cn.jicreate.jicreate_app.a2dde3930ff;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * 按附件 HTML 的 453 设计宽度逐项还原 ChatGPT 附件菜单。
 *
 * 原稿关键尺寸：页面顶距 30、左边距 14、行高 124、徽章直径 124、
 * 图标 70、图标在徽章内四边距 26、标签左边距 35、字号 44、行间距 46。
 * 所有尺寸均以 453 设计宽度为基准做等比换算，避免不同屏幕宽度下比例漂移。
 */
public class GptAttachMenuView extends View {

    public static final int ACTION_CAMERA = 0;
    public static final int ACTION_ALBUM = 1;
    public static final int ACTION_FILE = 2;
    public static final int ACTION_PLUGIN = 3;
    public static final int ACTION_THINK = 4;

    public interface Listener {
        void onAction(int action);

        void onDismiss();
    }

    private static final float DESIGN_WIDTH = 453f;
    private static final float TOP = 30f;
    private static final float LEFT = 14f;
    private static final float ITEM_HEIGHT = 124f;
    private static final float ITEM_GAP = 46f;
    private static final float BADGE_SIZE = 124f;
    private static final float ICON_SIZE = 70f;
    private static final float ICON_INSET = 26f;
    private static final float LABEL_GAP = 35f;
    private static final float LABEL_SIZE = 44f;
    private static final float LABEL_OFFSET_Y = 3f;
    private static final float TOAST_BOTTOM = 28f;
    private static final float TOAST_TEXT_SIZE = 15f;

    private static final int COLOR_PAGE = 0xFFFFFFFF;
    private static final int COLOR_BADGE = 0xFFE8E8E8;
    private static final int COLOR_BADGE_PRESSED = 0xFFDEDEDE;
    private static final int COLOR_INK = 0xFF0D0D0D;
    private static final int COLOR_TOAST = 0xE00D0D0D;

    private final int[] labelRes = {
            R.string.gpt_attach_camera,
            R.string.gpt_attach_album,
            R.string.gpt_attach_file,
            R.string.gpt_attach_plugin,
            R.string.gpt_attach_deep_think
    };
    private final int[] iconRes = {
            R.drawable.ic_attach_menu_camera,
            R.drawable.ic_attach_menu_photo,
            R.drawable.ic_attach_menu_file,
            R.drawable.ic_attach_menu_plugin,
            R.drawable.ic_attach_menu_think
    };

    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toastTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badgeRect = new RectF();
    private final RectF toastRect = new RectF();
    private final Rect iconRect = new Rect();
    private final Rect[] hitRects = new Rect[5];
    private final String[] labels = new String[5];
    private final Drawable[] icons = new Drawable[5];

    private float unit;
    private float stageLeft;
    private int pressedAction = -1;
    private boolean startedOnBlank;
    private Listener listener;

    private CharSequence toastText = "";
    private float toastAlpha;
    private float toastOffset;
    private ValueAnimator toastAnimator;

    public GptAttachMenuView(Context context) {
        this(context, null);
    }

    public GptAttachMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GptAttachMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(context.getString(R.string.gpt_attach_menu_description));

        Typeface baseTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
        Typeface typeface = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Typeface.create(baseTypeface, 450, false)
                : baseTypeface;
        labelPaint.setTypeface(typeface);
        labelPaint.setColor(COLOR_INK);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        toastTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        toastTextPaint.setColor(Color.WHITE);
        toastTextPaint.setTextAlign(Paint.Align.LEFT);
        toastPaint.setColor(COLOR_TOAST);

        for (int i = 0; i < labels.length; i++) {
            labels[i] = context.getString(labelRes[i]);
            hitRects[i] = new Rect();
            Drawable drawable = ContextCompat.getDrawable(context, iconRes[i]);
            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable.mutate());
                DrawableCompat.setTint(drawable, COLOR_INK);
            }
            icons[i] = drawable;
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 对应原稿底部的深色 toast：淡入、停留、淡出，共约 1.8 秒。 */
    public void flash(CharSequence text) {
        cancelToastAnimator();
        toastText = text == null ? "" : text;
        toastAlpha = 0f;
        toastOffset = 10f * unit;
        toastAnimator = ValueAnimator.ofFloat(0f, 1f);
        toastAnimator.setDuration(1800L);
        toastAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            if (fraction < 0.12f) {
                toastAlpha = fraction / 0.12f;
            } else if (fraction > 0.84f) {
                toastAlpha = (1f - fraction) / 0.16f;
            } else {
                toastAlpha = 1f;
            }
            float enter = Math.min(1f, fraction / 0.12f);
            toastOffset = (1f - enter) * 10f * unit;
            invalidate();
        });
        toastAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                toastAlpha = 0f;
                toastOffset = 0f;
                invalidate();
            }
        });
        toastAnimator.start();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unit = Math.min(getResources().getDisplayMetrics().density, w / DESIGN_WIDTH);
        stageLeft = Math.max(0f, (w - DESIGN_WIDTH * unit) * 0.5f);
        labelPaint.setTextSize(LABEL_SIZE * unit);
        toastTextPaint.setTextSize(TOAST_TEXT_SIZE * unit);
        rebuildHitRects();
    }

    private void rebuildHitRects() {
        for (int i = 0; i < hitRects.length; i++) {
            float top = TOP * unit + i * (ITEM_HEIGHT + ITEM_GAP) * unit;
            float left = stageLeft + LEFT * unit;
            float labelLeft = left + BADGE_SIZE * unit + LABEL_GAP * unit;
            float labelWidth = measureLabel(i);
            hitRects[i].set(
                    Math.round(left),
                    Math.round(top),
                    Math.round(labelLeft + labelWidth),
                    Math.round(top + ITEM_HEIGHT * unit));
        }
    }

    private float measureLabel(int index) {
        boolean spaced = index == ACTION_ALBUM || index == ACTION_THINK;
        labelPaint.setLetterSpacing(spaced ? 1.5f / LABEL_SIZE : 0f);
        float width = labelPaint.measureText(labels[index]);
        labelPaint.setLetterSpacing(0f);
        return width;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(COLOR_PAGE);
        if (unit <= 0f) {
            return;
        }

        for (int i = 0; i < labels.length; i++) {
            float top = TOP * unit + i * (ITEM_HEIGHT + ITEM_GAP) * unit;
            float left = stageLeft + LEFT * unit;
            float centerX = left + BADGE_SIZE * unit * 0.5f;
            float centerY = top + ITEM_HEIGHT * unit * 0.5f;

            canvas.save();
            if (pressedAction == i) {
                float scale = 0.965f;
                canvas.scale(scale, scale, centerX, centerY);
            }
            badgePaint.setColor(pressedAction == i ? COLOR_BADGE_PRESSED : COLOR_BADGE);
            badgeRect.set(
                    left,
                    top,
                    left + BADGE_SIZE * unit,
                    top + BADGE_SIZE * unit);
            canvas.drawOval(badgeRect, badgePaint);

            Drawable icon = icons[i];
            if (icon != null) {
                iconRect.set(
                        Math.round(left + ICON_INSET * unit),
                        Math.round(top + ICON_INSET * unit),
                        Math.round(left + (ICON_INSET + ICON_SIZE) * unit),
                        Math.round(top + (ICON_INSET + ICON_SIZE) * unit));
                icon.setBounds(iconRect);
                icon.draw(canvas);
            }
            canvas.restore();

            boolean spaced = i == ACTION_ALBUM || i == ACTION_THINK;
            labelPaint.setLetterSpacing(spaced ? 1.5f / LABEL_SIZE : 0f);
            Paint.FontMetrics metrics = labelPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) * 0.5f
                    - LABEL_OFFSET_Y * unit;
            float labelLeft = left + (BADGE_SIZE + LABEL_GAP) * unit;
            canvas.drawText(labels[i], labelLeft, baseline, labelPaint);
            labelPaint.setLetterSpacing(0f);
        }

        drawToast(canvas);
    }

    private void drawToast(Canvas canvas) {
        if (toastAlpha <= 0f || toastText.length() == 0) {
            return;
        }
        float textWidth = toastTextPaint.measureText(toastText.toString());
        Paint.FontMetrics metrics = toastTextPaint.getFontMetrics();
        float contentHeight = metrics.descent - metrics.ascent;
        float padH = 20f * unit;
        float padV = 10f * unit;
        float width = textWidth + padH * 2f;
        float height = contentHeight + padV * 2f;
        float left = (getWidth() - width) * 0.5f;
        float bottom = getHeight() - TOAST_BOTTOM * unit + toastOffset;
        float top = bottom - height;
        toastRect.set(left, top, left + width, bottom);

        toastPaint.setColor(COLOR_TOAST);
        toastPaint.setAlpha(Math.round(224f * Math.max(0f, Math.min(1f, toastAlpha))));
        float radius = 24f * unit;
        canvas.drawRoundRect(toastRect, radius, radius, toastPaint);

        toastTextPaint.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, toastAlpha))));
        float baseline = top + padV - metrics.ascent;
        canvas.drawText(toastText.toString(), left + padH, baseline, toastTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedAction = findAction(x, y);
                startedOnBlank = pressedAction < 0;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                int current = findAction(x, y);
                if (current != pressedAction) {
                    pressedAction = current;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int action = pressedAction;
                boolean dismiss = startedOnBlank && action < 0;
                clearPressState();
                if (action >= 0) {
                    performClick();
                    if (listener != null) {
                        listener.onAction(action);
                    }
                } else if (dismiss && listener != null) {
                    listener.onDismiss();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                clearPressState();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int findAction(float x, float y) {
        for (int i = 0; i < hitRects.length; i++) {
            if (hitRects[i].contains(Math.round(x), Math.round(y))) {
                return i;
            }
        }
        return -1;
    }

    private void clearPressState() {
        pressedAction = -1;
        startedOnBlank = false;
        invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
    }

    private void cancelToastAnimator() {
        if (toastAnimator != null) {
            toastAnimator.cancel();
            toastAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelToastAnimator();
        super.onDetachedFromWindow();
    }
}
