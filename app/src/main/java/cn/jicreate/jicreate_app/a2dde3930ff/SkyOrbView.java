package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * ChatGPT 语音页的“天空语音球”。
 *
 * 视觉部分并非贴图，而是把参考 HTML 中的 WebGL fragment shader 原样移植到
 * OpenGL ES 2.0：蓝色天空渐变、fbm 云层、右上积云、左下羽毛云、呼吸与颗粒。
 * 使用 TextureView + EGL 渲染，既能保留圆外的透明像素，也能和普通 View 正常叠放。
 */
public class SkyOrbView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface OnReadyListener {
        /** ready=true 表示已经画出第一帧；false 表示当前设备无法建立 GL 渲染。 */
        void onReady(boolean ready);
    }

    private static final String TAG = "SkyOrbView";

    private static final String VERTEX_SHADER =
            "attribute vec2 a_pos;\n" +
            "void main(){\n" +
            "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision highp float;\n" +
            "uniform vec2 u_res;\n" +
            "uniform float u_time;\n" +
            "uniform float u_level;\n" +
            "uniform float u_scale;\n" +
            "\n" +
            "float hash21(vec2 p){\n" +
            "  p = fract(p * vec2(123.34, 456.21));\n" +
            "  p += dot(p, p + 45.32);\n" +
            "  return fract(p.x * p.y);\n" +
            "}\n" +
            "float vnoise(vec2 p){\n" +
            "  vec2 i = floor(p);\n" +
            "  vec2 f = fract(p);\n" +
            "  vec2 u = f * f * (3.0 - 2.0 * f);\n" +
            "  float a = hash21(i);\n" +
            "  float b = hash21(i + vec2(1.0, 0.0));\n" +
            "  float c = hash21(i + vec2(0.0, 1.0));\n" +
            "  float d = hash21(i + vec2(1.0, 1.0));\n" +
            "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n" +
            "}\n" +
            "float fbm(vec2 p){\n" +
            "  float s = 0.0;\n" +
            "  float amp = 0.5;\n" +
            "  mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);\n" +
            "  for (int i = 0; i < 4; i++) {\n" +
            "    s += amp * vnoise(p);\n" +
            "    p = rot * p * 2.03;\n" +
            "    amp *= 0.5;\n" +
            "  }\n" +
            "  return s;\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "  vec2 uv = (gl_FragCoord.xy - 0.5 * u_res) / (0.5 * u_res.y);\n" +
            "  uv.y = -uv.y;\n" +
            "  vec2 suv = uv / u_scale;\n" +
            "  float radius = length(suv);\n" +
            "  float clipRadius = length(uv);\n" +
            "  float aa = 1.7 / (0.5 * u_res.y);\n" +
            // 参考稿的 canvas 外面还有一层 CSS border-radius:50% 圆形裁切，
            // 这里按 uv 半径裁圆，才能得到同样的正圆边缘而不是被方画布切平。
            "  if (clipRadius > 1.0 + aa) discard;\n" +
            "  float alpha = 1.0 - smoothstep(1.0 - aa, 1.0, clipRadius);\n" +
            "\n" +
            "  float t = suv.y * 0.5 + 0.5;\n" +
            "  float time = u_time;\n" +
            "  vec2 p = suv * vec2(1.45, 1.38);\n" +
            "  float ang = time * 0.030;\n" +
            "  mat2 R = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));\n" +
            "  vec2 q = R * p;\n" +
            "\n" +
            "  float w1 = fbm(q * 1.05 + vec2(1.7, 4.2) + vec2(time * 0.020, -time * 0.014));\n" +
            "  float w2 = fbm(q * 1.72 + vec2(8.3, 2.1) - vec2(time * 0.015, time * 0.022));\n" +
            "  float w3 = fbm(q * 3.35 + vec2(3.9, 9.1) + vec2(time * 0.030, time * 0.010));\n" +
            "\n" +
            "  float xw = suv.x + (w1 - 0.5) * 0.14;\n" +
            "  float tw = t + (w2 - 0.5) * 0.080;\n" +
            "  float xpos = max(0.0, xw);\n" +
            "  float line = 0.515 - 0.060 * xw - 0.190 * xpos - u_level * 0.018 + sin(time * 0.45) * 0.008;\n" +
            "  float width = 0.270 - 0.120 * xpos;\n" +
            "  float cloud = smoothstep(line - width * 0.5, line + width * 0.5, tw);\n" +
            "  cloud *= mix(0.965, 1.0, smoothstep(0.20, 0.80, w3));\n" +
            "  cloud = clamp(cloud + smoothstep(0.74, 1.0, t) * 0.08, 0.0, 1.0);\n" +
            "\n" +
            "  vec2 pc = vec2((suv.x - 0.80) / 0.36, (suv.y + 0.46) / 0.54);\n" +
            "  float puffMask = 1.0 - smoothstep(0.12, 1.05, length(pc));\n" +
            "  float puff = puffMask * smoothstep(0.32, 0.68, fbm(q * 2.15 + vec2(6.0, 1.0))) * 0.78;\n" +
            "  puff *= smoothstep(0.10, 0.22, t);\n" +
            "  cloud = clamp(cloud + puff, 0.0, 1.0);\n" +
            "\n" +
            "  float wx = smoothstep(-0.95, -0.68, suv.x) * (1.0 - smoothstep(-0.50, -0.20, suv.x));\n" +
            "  float fd = (tw - 0.60 - 0.08 * suv.x) / 0.10;\n" +
            "  float feather = exp(-fd * fd);\n" +
            "  float featherN = smoothstep(0.50, 0.78, fbm(vec2(q.x * 2.9, q.y * 1.2) + vec2(2.0, 7.0) + time * 0.010));\n" +
            "  cloud = clamp(cloud + feather * featherN * wx * 0.08, 0.0, 1.0);\n" +
            "\n" +
            "  vec3 blueTop = vec3(0.298, 0.478, 0.929);\n" +
            "  vec3 blueMid = vec3(0.322, 0.502, 0.937);\n" +
            "  vec3 blue = mix(blueTop, blueMid, smoothstep(0.0, 0.60, t));\n" +
            "  blue = mix(blue, vec3(0.326, 0.510, 0.945), smoothstep(-0.25, 0.95, suv.x) * 0.34);\n" +
            "  blue *= 1.0 - smoothstep(0.55, 0.90, w2) * 0.008;\n" +
            "\n" +
            "  vec3 cloudLight = vec3(0.898, 0.929, 0.996);\n" +
            "  vec3 cloudShade = vec3(0.725, 0.788, 0.976);\n" +
            "  float coreT = line + width * 0.38;\n" +
            "  float shadeMix = smoothstep(0.04, 0.34, tw - coreT);\n" +
            "  vec3 cloudCol = mix(cloudLight, cloudShade, shadeMix);\n" +
            "  cloudCol *= 0.988 + 0.018 * w3;\n" +
            "  cloudCol += (w1 - 0.5) * 0.010;\n" +
            "\n" +
            "  vec3 col = mix(blue, cloudCol, cloud);\n" +
            "  col += vec3(0.010, 0.014, 0.026) * (1.0 - smoothstep(0.0, 0.92, radius)) * (0.45 + 0.55 * u_level);\n" +
            "\n" +
            "  float rim = smoothstep(0.86, 1.0, radius);\n" +
            "  col *= 1.0 - rim * 0.018;\n" +
            "  float grain = (hash21(gl_FragCoord.xy + fract(time) * 17.0) - 0.5) * 0.003;\n" +
            "  col += grain;\n" +
            "  gl_FragColor = vec4(col * alpha, alpha);\n" +
            "}";

    /** 少量老 GPU 的 fragment shader 仅支持 mediump，退回该精度仍能保留动态云层。 */
    private static final String FRAGMENT_SHADER_LOWP =
            FRAGMENT_SHADER.replace("precision highp float;", "precision mediump float;");

    private static final int EGL_OPENGL_ES2_BIT = 0x0004;

    private volatile boolean live = true;
    private volatile boolean released;
    private volatile boolean firstFrameDrawn;
    private volatile long tapAtNanos;
    private float level = 0.16f;

    private RenderThread renderThread;
    private OnReadyListener readyListener;
    private volatile boolean readyReported;
    private final Runnable readyWatchdog = () -> {
        if (!firstFrameDrawn && isShown()) {
            reportReady(false);
        }
    };

    public SkyOrbView(Context context) {
        this(context, null);
    }

    public SkyOrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TextureView 必须关闭 opaque，圆外的透明像素才能和页面背景正确合成。
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    public void setOnReadyListener(OnReadyListener listener) {
        readyListener = listener;
    }

    /** 语音页处于“聆听中”时呼吸幅度更大；退出页面后只保留极轻的待机波动。 */
    public void setLive(boolean value) {
        live = value;
    }

    /** 点击球体时给一次短促的能量脉冲，让云层呼吸有反馈。 */
    public void pulse() {
        tapAtNanos = SystemClock.elapsedRealtimeNanos();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        firstFrameDrawn = false;
        startRenderer(surface);
        // 驱动异常时不要留下空白：4 秒内没有第一帧就交给静态兜底图。
        removeCallbacks(readyWatchdog);
        postDelayed(readyWatchdog, 4000L);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // 渲染线程每帧都从 View 读取尺寸并更新 viewport，这里无需额外处理。
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        removeCallbacks(readyWatchdog);
        stopRenderer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void startRenderer(SurfaceTexture surface) {
        if (renderThread != null) {
            return;
        }
        released = false;
        readyReported = false;
        RenderThread thread = new RenderThread(surface);
        renderThread = thread;
        thread.start();
    }

    private void stopRenderer() {
        RenderThread thread = renderThread;
        renderThread = null;
        if (thread != null) {
            thread.shutdown();
            try {
                thread.join(700);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        released = true;
        removeCallbacks(readyWatchdog);
        stopRenderer();
        super.onDetachedFromWindow();
    }

    private boolean shouldRender() {
        return !released && isShown() && getWindowVisibility() == VISIBLE;
    }

    private void reportReady(final boolean ready) {
        if (readyReported) {
            return;
        }
        readyReported = true;
        post(() -> {
            if (readyListener != null) {
                readyListener.onReady(ready);
            }
        });
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float simulate(float time, boolean liveNow) {
        float breath = 0.40f
                + 0.190f * (float) Math.sin(time * 1.13f + 0.4f)
                + 0.115f * (float) Math.sin(time * 2.57f + 1.35f)
                + 0.060f * (float) Math.sin(time * 4.41f + 2.1f)
                + 0.030f * (float) Math.sin(time * 7.10f + 0.65f);
        float idle = 0.155f
                + 0.045f * (float) Math.sin(time * 0.85f + 0.6f)
                + 0.020f * (float) Math.sin(time * 1.9f);
        return clamp(liveNow ? breath : idle, 0.03f, 1f);
    }

    private final class RenderThread extends Thread {

        private final SurfaceTexture surfaceTexture;
        private volatile boolean running = true;

        RenderThread(SurfaceTexture surfaceTexture) {
            super("SkyOrbRender");
            this.surfaceTexture = surfaceTexture;
        }

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            EGLDisplay display = EGL14.EGL_NO_DISPLAY;
            EGLContext context = EGL14.EGL_NO_CONTEXT;
            EGLSurface surface = EGL14.EGL_NO_SURFACE;
            int program = 0;
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (display == EGL14.EGL_NO_DISPLAY) {
                    reportReady(false);
                    return;
                }
                int[] version = new int[2];
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                    reportReady(false);
                    return;
                }

                int[] configAttribs = new int[]{
                        EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_DEPTH_SIZE, 0,
                        EGL14.EGL_STENCIL_SIZE, 0,
                        EGL14.EGL_NONE
                };
                EGLConfig[] configs = new EGLConfig[1];
                int[] configCount = new int[1];
                if (!EGL14.eglChooseConfig(display, configAttribs, 0,
                        configs, 0, 1, configCount, 0) || configCount[0] == 0) {
                    reportReady(false);
                    return;
                }
                int[] contextAttribs = new int[]{
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };
                context = EGL14.eglCreateContext(display, configs[0],
                        EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
                if (context == EGL14.EGL_NO_CONTEXT) {
                    reportReady(false);
                    return;
                }
                int[] surfaceAttribs = new int[]{EGL14.EGL_NONE};
                surface = EGL14.eglCreateWindowSurface(display, configs[0],
                        surfaceTexture, surfaceAttribs, 0);
                if (surface == EGL14.EGL_NO_SURFACE) {
                    reportReady(false);
                    return;
                }
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    reportReady(false);
                    return;
                }

                program = buildProgram();
                if (program == 0) {
                    reportReady(false);
                    return;
                }
                int positionLocation = GLES20.glGetAttribLocation(program, "a_pos");
                int resLocation = GLES20.glGetUniformLocation(program, "u_res");
                int timeLocation = GLES20.glGetUniformLocation(program, "u_time");
                int levelLocation = GLES20.glGetUniformLocation(program, "u_level");
                int scaleLocation = GLES20.glGetUniformLocation(program, "u_scale");

                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                GLES20.glClearColor(0f, 0f, 0f, 0f);

                int buffer = createQuadBuffer(positionLocation);
                if (buffer == 0) {
                    reportReady(false);
                    return;
                }

                long startNanos = SystemClock.elapsedRealtimeNanos();
                long lastFrameNanos = 0L;
                while (running) {
                    if (!shouldRender()) {
                        sleepQuietly(90);
                        continue;
                    }
                    long now = SystemClock.elapsedRealtimeNanos();
                    if (now - lastFrameNanos < 15000000L) {
                        sleepQuietly(2);
                        continue;
                    }
                    int width = getWidth();
                    int height = getHeight();
                    if (width <= 0 || height <= 0) {
                        sleepQuietly(40);
                        continue;
                    }
                    lastFrameNanos = now;

                    float elapsed = (now - startNanos) / 1000000000f;
                    float target = simulate(elapsed, live);
                    if (tapAtNanos > 0) {
                        long sinceTap = now - tapAtNanos;
                        if (sinceTap < 900000000L) {
                            float progress = sinceTap / 900000000f;
                            target = clamp(target + 0.16f * (1f - progress), 0.03f, 1f);
                        }
                    }
                    level += (target - level) * 0.085f;
                    level = clamp(level, 0.02f, 1f);

                    GLES20.glViewport(0, 0, width, height);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glUseProgram(program);
                    GLES20.glUniform2f(resLocation, width, height);
                    GLES20.glUniform1f(timeLocation, elapsed);
                    GLES20.glUniform1f(levelLocation, level);
                    GLES20.glUniform1f(scaleLocation, 1f + level * 0.012f + 0.002f);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                    if (!EGL14.eglSwapBuffers(display, surface)) {
                        break;
                    }
                    if (!firstFrameDrawn) {
                        firstFrameDrawn = true;
                        reportReady(true);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "sky orb renderer stopped", t);
                reportReady(false);
            } finally {
                if (program != 0) {
                    GLES20.glDeleteProgram(program);
                }
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface);
                    }
                    if (context != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, context);
                    }
                    EGL14.eglTerminate(display);
                }
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int createQuadBuffer(int positionLocation) {
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        if (buffers[0] == 0) {
            return 0;
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        float[] quad = new float[]{
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };
        java.nio.FloatBuffer data = java.nio.ByteBuffer
                .allocateDirect(quad.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        data.put(quad).position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.length * 4,
                data, GLES20.GL_STATIC_DRAW);
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT,
                false, 0, 0);
        return buffers[0];
    }

    private static int buildProgram() {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (fragment == 0) {
            fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_LOWP);
        }
        if (vertex == 0 || fragment == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            Log.w(TAG, "program link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.w(TAG, "shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
