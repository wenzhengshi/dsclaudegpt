package cn.jicreate.jicreate_app.a2dde3930ff;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 会话标题栏参考稿的页面底色：横向 #F5F5F5 → #FCFCFC → #EEEEEE 的渐变。
 *
 * <p>参考稿用 23 个色标精确描述这条渐变，这里按相同色标绘制，
 * 并以控件实际宽度作为渐变长度，保证不同屏宽下比例一致。</p>
 */
public final class GptNavBackdropDrawable extends Drawable {

    /** 与参考稿 :root --backdrop 的 23 个色标一一对应。 */
    private static final int[] COLORS = {
            0xFFF6F6F6, 0xFFF5F5F5, 0xFFF6F6F6, 0xFFF7F7F7, 0xFFF9F9F9,
            0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC, 0xFFFBFBFB, 0xFFFAFAFA,
            0xFFF9F9F9, 0xFFF8F8F8, 0xFFF6F6F6, 0xFFF4F4F4, 0xFFF3F3F3,
            0xFFF1F1F1, 0xFFF0F0F0, 0xFFEFEFEF, 0xFFEFEFEF, 0xFFF0F0F0,
            0xFFF1F1F1, 0xFFF3F3F3, 0xFFF4F4F4
    };

    private static final float[] POSITIONS = {
            0.00f, 0.09f, 0.19f, 0.22f, 0.26f,
            0.31f, 0.38f, 0.44f, 0.50f, 0.56f,
            0.60f, 0.63f, 0.66f, 0.69f, 0.72f,
            0.76f, 0.80f, 0.84f, 0.87f, 0.91f,
            0.94f, 0.98f, 1.00f
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gradientWidth = -1;

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        if (gradientWidth != bounds.width()) {
            gradientWidth = bounds.width();
            paint.setShader(new LinearGradient(0f, 0f, gradientWidth, 0f,
                    COLORS, POSITIONS, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(bounds, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
