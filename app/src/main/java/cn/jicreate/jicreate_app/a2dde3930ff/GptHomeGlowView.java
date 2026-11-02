package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 空对话首页的顶部光晕。
 *
 * <p>参考稿以 1080 为设计宽度：页面底色 #F5F5F5，顶部中心 #FCFCFC，
 * 横向从 #F5F5F5 → #F9F9F9 → #FCFCFC → #F9F9F9 → #F5F5F5；
 * 纵向在 17.87037cqw（193）之前完全显示，到 27.5cqw（297）柔和淡出。</p>
 *
 * <p>淡出用同色 #F5F5F5 的半透明叠加实现，等价于参考稿的 mask-image，
 * 这样无需 saveLayer，绘制成本更低。</p>
 */
public class GptHomeGlowView extends View {

    private static final float DESIGN_WIDTH = 1080f;
    private static final float GLOW_HEIGHT = 297f;
    private static final float FADE_START = 193f;

    private static final int EDGE = 0xFFF5F5F5;
    private static final int SOFT = 0xFFF9F9F9;
    private static final int CENTER = 0xFFFCFCFC;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient horizontal;
    private LinearGradient fade;

    public GptHomeGlowView(Context context) {
        this(context, null);
    }

    public GptHomeGlowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GptHomeGlowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int height = Math.round(GLOW_HEIGHT * width / DESIGN_WIDTH);
        setMeasuredDimension(width, Math.max(height, 1));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        horizontal = new LinearGradient(0f, 0f, w, 0f,
                new int[]{EDGE, SOFT, CENTER, SOFT, EDGE},
                new float[]{0f, 0.45f, 0.5f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        float start = Math.min(1f, FADE_START / GLOW_HEIGHT);
        fade = new LinearGradient(0f, 0f, 0f, h,
                new int[]{0x00F5F5F5, 0xFFF5F5F5},
                new float[]{start, 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || horizontal == null) {
            return;
        }
        paint.setShader(horizontal);
        canvas.drawRect(0f, 0f, w, h, paint);
        if (fade != null) {
            paint.setShader(fade);
            canvas.drawRect(0f, 0f, w, h, paint);
        }
        paint.setShader(null);
    }
}
