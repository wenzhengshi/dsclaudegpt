package cn.jicreate.jicreate_app.a2dde3930ff;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * 天空球背后的蓝色光晕。
 *
 * 对应参考 HTML 的 .orb-halo：132% 尺寸、四段径向渐变、出现时轻微放大，
 * 随后按 4.6s 周期做 scale 1 → 1.045、opacity .92 → .66 的呼吸。
 */
public class OrbHaloView extends View {

    private static final int[] COLORS = new int[]{
            0x385B89F2, // rgba(91,137,242,.22)
            0x1C5B89F2, // rgba(91,137,242,.11)
            0x095B89F2, // rgba(91,137,242,.035)
            0x005B89F2  // transparent
    };
    private static final float[] STOPS = new float[]{
            0f, 34f / 70f, 54f / 70f, 1f
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator breathe;

    public OrbHaloView(Context context) {
        this(context, null);
    }

    public OrbHaloView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        setAlpha(0f);
        setScaleX(0.9f);
        setScaleY(0.9f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        // CSS 的 radial-gradient 默认按 farthest-corner 计算半径（0.7071 * 边长），
        // 70% 处完全透明，所以渐变实际在 0.495 * 边长处结束。
        float radius = Math.min(w, h) * 0.495f;
        paint.setShader(new RadialGradient(w / 2f, h / 2f, radius,
                COLORS, STOPS, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, paint);
    }

    /** 进入语音页：按参考稿先淡入放大，再开始呼吸。 */
    public void show() {
        cancelAnimations();
        setVisibility(VISIBLE);
        if (isBreatheDisabled()) {
            setAlpha(0.8f);
            setScaleX(1f);
            setScaleY(1f);
            return;
        }
        setAlpha(0f);
        setScaleX(0.9f);
        setScaleY(0.9f);
        animate()
                .alpha(0.92f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1100L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::startBreathing)
                .start();
    }

    /** 离开语音页：立刻隐藏，避免在后台继续跑动画。 */
    public void hide() {
        cancelAnimations();
        setAlpha(0f);
        setVisibility(GONE);
    }

    private void startBreathing() {
        if (!isShown() || isBreatheDisabled()) {
            return;
        }
        breathe = ValueAnimator.ofFloat(0f, 1f);
        breathe.setDuration(2300L);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = 1f + 0.045f * fraction;
            setScaleX(scale);
            setScaleY(scale);
            setAlpha(0.92f - 0.26f * fraction);
        });
        breathe.start();
    }

    private void cancelAnimations() {
        if (breathe != null) {
            breathe.cancel();
            breathe = null;
        }
        animate().cancel();
    }

    private static boolean isBreatheDisabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !ValueAnimator.areAnimatorsEnabled();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimations();
        super.onDetachedFromWindow();
    }
}
