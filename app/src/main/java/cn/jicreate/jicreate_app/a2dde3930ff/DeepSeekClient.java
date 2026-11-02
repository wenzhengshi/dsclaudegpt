package cn.jicreate.jicreate_app.a2dde3930ff;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 直连 DeepSeek（OpenAI 兼容）接口的极简客户端。
 * 使用 HttpURLConnection + SSE 流式解析，不依赖任何第三方网络库，
 * 数据从手机直接发往用户配置的接口地址，不经过任何中转服务器。
 */
public class DeepSeekClient {

    public interface Callback {
        /** 流式增量文本（后台线程回调，调用方需切回主线程）。 */
        void onDelta(String delta);

        /** 生成完成，full 为完整文本。 */
        void onDone(String full);

        /** 请求失败，code 为 HTTP 状态码（0 表示网络异常）。 */
        void onError(String message, int code);

        /** 主模型不可用、已自动切换到备用模型时回调。 */
        void onModelSwitched(String model);

        /**
         * 当前接口拒绝了图片输入（例如换成了纯文本模型），
         * 客户端已自动去掉图片、改为纯文本重试。
         */
        default void onImagesDropped() {
        }

        /**
         * 流式思考增量（reasoning_content）。开启思考后模型会先输出思考过程，
         * 这段文字与正式回答分开回调，界面可以单独展示、折叠。
         */
        default void onReasoningDelta(String delta) {
        }

        /**
         * 模型只返回了思考内容、没有返回正文。客户端不会把这当成失败，
         * 而是把思考内容作为回答交给界面展示；调用方应隐藏重复的思考卡片。
         */
        default void onReasoningAsAnswer() {
        }

        /**
         * 已经确认这是一个真正的 text/event-stream 响应，并且收到了首个事件。
         * 界面可以据此结束“正在连接”状态。
         */
        default void onStreamStart() {
        }

        /**
         * 接口没有返回 SSE，而是返回了整段 JSON（通常是网关或模型不支持流式）。
         * 此时内容无法逐字推送，调用方应提示用户，而不是假装在流式输出。
         */
        default void onNonStream() {
        }

        /**
         * 还没有收到任何内容就失败了（超时 / 网络重置 / 5xx），客户端正在自动重试。
         * attempt 从 1 开始。此时界面上没有任何已生成内容，直接重试即可。
         */
        default void onRetry(int attempt, String reason) {
        }

        /**
         * 已经收到部分内容后连接中断，客户端正带着已生成内容去断点续写。
         * 调用方必须保留界面上已有的文字，只把后续增量继续追加。
         */
        default void onAutoResume(int attempt, String partial) {
        }

        /**
         * 流正常结束但没有结束标记，说明内容可能被截断（已收到的部分仍然有效）。
         * 调用方可以提示用户“可能不完整”，并允许继续生成。
         */
        default void onIncomplete() {
        }
    }

    public static class Msg {
        public final String role;
        public final String content;
        /** 随消息发送的图片，元素为 data:image/...;base64,... 形式。 */
        public final List<String> images;

        public Msg(String role, String content) {
            this(role, content, null);
        }

        public Msg(String role, String content, List<String> images) {
            this.role = role;
            this.content = content == null ? "" : content;
            this.images = images == null
                    ? java.util.Collections.emptyList() : new java.util.ArrayList<>(images);
        }

        public boolean hasImages() {
            return !images.isEmpty();
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 20000;
    /**
     * 读超时。思考模型在首个 token 之前可能沉默很久，长回答中途也可能长时间没有新事件，
     * 所以这里给到 5 分钟；即便真的超时，也会自动带着已生成内容续写，而不是直接失败。
     */
    private static final int READ_TIMEOUT_MS = 300000;
    /** 非流式响应（整段 JSON）的读取上限，避免异常接口把内存吃满。 */
    private static final int MAX_WHOLE_BODY_BYTES = 4 * 1024 * 1024;
    /** 一个字节都没收到就失败时，最多自动重试几次（超时/网络重置/5xx）。 */
    private static final int MAX_AUTO_RETRY = 2;
    /** 已经收到部分内容后连接中断，最多自动断点续写几次。 */
    private static final int MAX_AUTO_RESUME = 3;
    /** 没有结束标记的“干净断开”只额外续写一次，避免对不规范网关每次都多发请求。 */
    private static final int MAX_EOF_RESUME = 1;
    /** 断点续写时提示模型接着写，不要重复已经输出的内容。 */
    public static final String CONTINUE_PROMPT =
            "（连接刚才中断了）请从断点处直接继续输出剩余内容：不要重复已经输出过的文字，"
                    + "不要重新开头，不要解释原因，也不要添加任何额外说明。";

    private final AtomicLong generation = new AtomicLong(0);
    private volatile HttpURLConnection activeConnection;
    private volatile InputStream activeStream;

    /** 已经推送给界面的正文，用于停止/断网时精确取回已生成的部分。 */
    private final Object liveLock = new Object();
    private final StringBuilder liveBuffer = new StringBuilder();

    /** 取回本次生成已经推送出去的全部正文（停止按钮、Activity 退出时保存草稿用）。 */
    public String snapshotText() {
        synchronized (liveLock) {
            return liveBuffer.toString();
        }
    }

    private void resetLive() {
        synchronized (liveLock) {
            liveBuffer.setLength(0);
        }
    }

    private void seedLive(String text) {
        synchronized (liveLock) {
            liveBuffer.setLength(0);
            if (text != null) {
                liveBuffer.append(text);
            }
        }
    }

    private void appendLive(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (liveLock) {
            liveBuffer.append(text);
        }
    }

    public void cancel() {
        generation.incrementAndGet();
        InputStream stream = activeStream;
        activeStream = null;
        if (stream != null) {
            try {
                // 关闭底层流能让阻塞中的 read() 立即返回，停止按钮才能真的停下来。
                stream.close();
            } catch (Exception ignored) {
                // 忽略取消时的异常
            }
        }
        HttpURLConnection connection = activeConnection;
        activeConnection = null;
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // 忽略取消时的异常
            }
        }
    }

    /**
     * 发起流式请求。若模型不存在，会自动切换到 fallbackModel 再试一次。
     */
    public void stream(final String apiKey, final String baseUrl, final String model,
                       final String fallbackModel, final List<Msg> messages,
                       final Callback callback) {
        stream(apiKey, baseUrl, model, fallbackModel, messages,
                false, Prefs.THINK_LEVEL_HIGH, null, callback);
    }

    /**
     * 发起流式请求，thinking 为 true 时向接口声明开启思考，并附带强度
     * （reasoning_effort）。若当前网关不认识这些参数，会自动逐级去掉后重试，
     * 保证在只支持普通对话的模型上仍然可用。
     */
    public void stream(final String apiKey, final String baseUrl, final String model,
                       final String fallbackModel, final List<Msg> messages,
                       final boolean thinking, final int thinkLevel,
                       final Callback callback) {
        stream(apiKey, baseUrl, model, fallbackModel, messages, thinking, thinkLevel,
                null, callback);
    }

    /**
     * 带“已生成内容”的流式请求：continuationPrefix 是界面上已经显示的文字，
     * 本次只把新输出追加在后面，并自动去掉与已有内容重复的开头。
     * 用户点“继续生成”、或断网后自动续写时使用。
     */
    public void stream(final String apiKey, final String baseUrl, final String model,
                       final String fallbackModel, final List<Msg> messages,
                       final boolean thinking, final int thinkLevel,
                       final String continuationPrefix, final Callback callback) {
        final long myGeneration = generation.incrementAndGet();
        seedLive(continuationPrefix);
        Thread thread = new Thread(() -> runStream(myGeneration, apiKey, baseUrl, model,
                fallbackModel, messages, thinking, thinkLevel, continuationPrefix, callback),
                "deepseek-stream");
        thread.setDaemon(true);
        thread.start();
    }

    private void runStream(long myGeneration, String apiKey, String baseUrl, String model,
                           String fallbackModel, List<Msg> messages, boolean thinking,
                           int thinkLevel, String continuationPrefix, Callback callback) {
        List<Msg> current = messages;
        String usedModel = model;
        Attempt attempt = attemptStream(myGeneration, apiKey, baseUrl, usedModel, current,
                callback, true, thinking, thinkLevel, continuationPrefix);
        if (generation.get() != myGeneration) {
            return;
        }
        if (!attempt.ok && attempt.imageError && hasImages(current)) {
            // 当前模型不接受图片（例如用户把模型改成了纯文本模型），
            // 去掉图片后用同一模型重试，保证对话仍能继续。
            callback.onImagesDropped();
            current = withoutImages(current);
            attempt = attemptStream(myGeneration, apiKey, baseUrl, usedModel, current,
                    callback, true, thinking, thinkLevel, continuationPrefix);
            if (generation.get() != myGeneration) {
                return;
            }
        }
        if (!attempt.ok && attempt.modelError && fallbackModel != null
                && !fallbackModel.trim().isEmpty()
                && !fallbackModel.trim().equalsIgnoreCase(model)) {
            callback.onModelSwitched(fallbackModel.trim());
            usedModel = fallbackModel.trim();
            if (generation.get() != myGeneration) {
                return;
            }
            attempt = attemptStream(myGeneration, apiKey, baseUrl, usedModel,
                    current, callback, true, thinking, thinkLevel, continuationPrefix);
            if (generation.get() != myGeneration) {
                return;
            }
            if (!attempt.ok && attempt.imageError && hasImages(current)) {
                callback.onImagesDropped();
                current = withoutImages(current);
                attempt = attemptStream(myGeneration, apiKey, baseUrl, usedModel,
                        current, callback, true, thinking, thinkLevel, continuationPrefix);
                if (generation.get() != myGeneration) {
                    return;
                }
            }
        }
        // 超时 / 断网 / 服务端 5xx：先自动重试或断点续写，实在不行才报错。
        attempt = recover(myGeneration, apiKey, baseUrl, usedModel, current, thinking,
                thinkLevel, callback, attempt);
        if (generation.get() != myGeneration) {
            return;
        }
        if (attempt.ok) {
            callback.onDone(attempt.full);
            return;
        }
        if (attempt.interrupted && !attempt.full.isEmpty()) {
            // 自动续写也没能接上：绝不丢弃已生成的内容，交给界面标记“可能不完整”。
            callback.onIncomplete();
            callback.onDone(attempt.full);
            return;
        }
        callback.onError(attempt.error, attempt.code);
    }

    /**
     * 失败后的自愈：
     * 1）完全没收到内容 → 原请求自动重试（超时、网络重置、429/5xx）；
     * 2）已经收到部分内容 → 带着这部分内容去断点续写，绝不丢弃。
     */
    private Attempt recover(long myGeneration, String apiKey, String baseUrl, String model,
                            List<Msg> messages, boolean thinking, int thinkLevel,
                            Callback callback, Attempt attempt) {
        int retries = 0;
        while (!attempt.ok && attempt.retryable && attempt.full.isEmpty()
                && retries < MAX_AUTO_RETRY) {
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            retries++;
            callback.onRetry(retries, attempt.error);
            if (!sleep(800L * retries)) {
                return Attempt.cancelled();
            }
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            attempt = attemptStreamOnce(myGeneration, apiKey, baseUrl, model, messages,
                    callback, true, thinking, thinkLevel, 0, null);
        }
        int resumes = 0;
        int eofResumes = 0;
        while (!attempt.ok && attempt.interrupted && !attempt.full.isEmpty()
                && resumes < MAX_AUTO_RESUME) {
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            if (attempt.eofTruncated && eofResumes >= MAX_EOF_RESUME) {
                break;
            }
            resumes++;
            if (attempt.eofTruncated) {
                eofResumes++;
            }
            callback.onAutoResume(resumes, attempt.full);
            // 稍等再续写：网络刚断时立刻重连往往还是失败。
            if (!sleep(600L * resumes)) {
                return Attempt.cancelled();
            }
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            List<Msg> cont = new java.util.ArrayList<>(messages.size() + 2);
            cont.addAll(messages);
            cont.add(new Msg("assistant", attempt.full));
            cont.add(new Msg("user", CONTINUE_PROMPT));
            Attempt next = attemptStream(myGeneration, apiKey, baseUrl, model, cont,
                    callback, true, thinking, thinkLevel, attempt.full);
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            if (next.ok) {
                return next;
            }
            // 续写又断了：保留这次拿到的更多内容继续尝试。
            if (next.interrupted && next.full.length() > attempt.full.length()) {
                attempt = next;
                continue;
            }
            if (!next.interrupted) {
                return next;
            }
        }
        return attempt;
    }

    /** 可中断的等待；返回 false 表示本次生成已被取消。 */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private static boolean hasImages(List<Msg> messages) {
        for (Msg message : messages) {
            if (message.hasImages()) {
                return true;
            }
        }
        return false;
    }

    private static List<Msg> withoutImages(List<Msg> messages) {
        List<Msg> result = new java.util.ArrayList<>(messages.size());
        for (Msg message : messages) {
            result.add(new Msg(message.role, message.content));
        }
        return result;
    }

    /**
     * 先按“思考开 + 强度”请求；若网关不认识参数（HTTP 400/422 且错误信息提到
     * thinking / reasoning），依次去掉 reasoning_effort、再去掉 thinking 重试。
     */
    private Attempt attemptStream(long myGeneration, String apiKey, String baseUrl,
                                  String model, List<Msg> messages, Callback callback,
                                  boolean allowDelta, boolean thinking, int thinkLevel,
                                  String continuationPrefix) {
        Attempt attempt = attemptStreamOnce(myGeneration, apiKey, baseUrl, model, messages,
                callback, allowDelta, thinking, thinkLevel, 0, continuationPrefix);
        int mode = 0;
        while (!attempt.ok && thinking && mode < 2 && isThinkingParamError(attempt)) {
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            mode++;
            attempt = attemptStreamOnce(myGeneration, apiKey, baseUrl, model, messages,
                    callback, allowDelta, thinking, thinkLevel, mode, continuationPrefix);
        }
        return attempt;
    }

    private Attempt attemptStreamOnce(long myGeneration, String apiKey, String baseUrl,
                                      String model, List<Msg> messages, Callback callback,
                                      boolean allowDelta, boolean thinking, int thinkLevel,
                                      int thinkParamMode, String continuationPrefix) {
        HttpURLConnection connection = null;
        try {
            JSONObject payload = buildPayload(model, messages, true, thinking, thinkLevel,
                    thinkParamMode);
            connection = openConnection(apiKey, baseUrl, payload);
            activeConnection = connection;
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String body = readAll(connection.getErrorStream(), 6000);
                String serverMessage = extractErrorMessage(body);
                return Attempt.failure(status, friendlyError(status, serverMessage),
                        isModelError(status, serverMessage), isImageError(status, serverMessage),
                        serverMessage);
            }

            InputStream input = connection.getInputStream();
            activeStream = input;
            // 不依赖 Content-Type 做硬判断：部分网关会漏标或标错。
            // 统一按 SSE 边读边解析；若读完一个事件都没有，再把原文当整段 JSON 回退，
            // 但绝不把整段内容伪装成逐字流式。
            StreamOutcome outcome = new StreamOutcome(continuationPrefix);
            StringBuilder raw = new StringBuilder();
            readSse(input, myGeneration, callback, allowDelta, outcome, raw);
            if (outcome.error == null && !outcome.cancelled && outcome.events == 0
                    && raw.length() > 0) {
                parseWholeBody(raw.toString(), callback, allowDelta, outcome);
                if (outcome.hasNewContent() || outcome.reasoning.length() > 0) {
                    // 整段 JSON 本身就是一次完整回答，不按“截断”处理。
                    // 只返回 reasoning_content 的整段 JSON 同样视为完整返回。
                    outcome.completed = true;
                    callback.onNonStream();
                }
            }
            // 续写时为了去掉与已有内容的重复，开头会短暂缓冲几十个字符，这里补发。
            flushContinuationFilter(callback, allowDelta, outcome);
            if (generation.get() != myGeneration || outcome.cancelled) {
                return Attempt.cancelled();
            }
            if (outcome.error != null && !outcome.error.isEmpty()) {
                if (outcome.hasNewContent()) {
                    // 已经收到内容后断开：把这一部分原样交回，交给上层续写，绝不清空。
                    return Attempt.interrupted(outcome.full.toString(),
                            outcome.reasoning.toString(), outcome.error, 0, false);
                }
                // 有些网关只把思考内容写进 reasoning_content：即使随后断网，
                // 已经拿到的思考内容也不该被当成“生成失败”丢掉。
                Attempt reasoningOnly = fallbackToReasoning(outcome, callback, allowDelta, true);
                if (reasoningOnly != null) {
                    return reasoningOnly;
                }
                return Attempt.failure(0, outcome.error, false);
            }
            if (!outcome.hasNewContent()) {
                // 模型只返回了 reasoning_content、content 为空。
                // 这不是请求失败：部分兼容网关/思考模型就是这种返回格式，
                // 把思考内容直接作为回答展示，而不是弹“生成失败”。
                Attempt reasoningOnly = fallbackToReasoning(outcome, callback, allowDelta,
                        outcome.truncated || !outcome.completed);
                if (reasoningOnly != null) {
                    return reasoningOnly;
                }
                return Attempt.failure(0, "模型没有返回任何内容，请稍后重试。", false);
            }
            if (!outcome.completed || outcome.truncated) {
                // 没有 [DONE]/finish_reason，或 finish_reason 明确为 length：
                // 内容可能被截断，保留并标记为不完整。
                return Attempt.interrupted(outcome.full.toString(),
                        outcome.reasoning.toString(), outcome.truncated
                                ? "回答达到长度上限，已保留已生成的内容。"
                                : "连接已断开，回答可能不完整。", 0, true);
            }
            return Attempt.success(outcome.full.toString(), outcome.reasoning.toString());
        } catch (SocketTimeoutException e) {
            String partial = snapshotText();
            if (!partial.isEmpty()) {
                return Attempt.interrupted(partial, "", "连接超时，已保留已生成的内容。", 0,
                        false);
            }
            return Attempt.failure(0, "连接超时，请检查网络后重试。", false);
        } catch (Exception e) {
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String partial = snapshotText();
            if (!partial.isEmpty()) {
                return Attempt.interrupted(partial, "", "网络连接中断：" + trim(detail, 160),
                        0, false);
            }
            return Attempt.failure(0, "网络连接失败：" + trim(detail, 160), false);
        } finally {
            InputStream stream = activeStream;
            activeStream = null;
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
            }
            if (activeConnection == connection) {
                activeConnection = null;
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 部分模型/网关会把真正的回答写进 reasoning_content，而 content 始终为空。
     * 这种情况不能弹“生成失败”：把思考内容按回答交给界面，并在输出可能不完整时
     * 同时标记为半成品，让用户仍然可以继续生成。
     *
     * @return null 表示没有任何可用的思考内容，调用方应按原有错误处理。
     */
    private Attempt fallbackToReasoning(StreamOutcome outcome, Callback callback,
                                        boolean allowDelta, boolean incomplete) {
        String reasoning = outcome.reasoning.toString().trim();
        if (reasoning.isEmpty()) {
            return null;
        }
        String addition;
        if (outcome.full.length() == 0
                || outcome.full.charAt(outcome.full.length() - 1) == '\n') {
            addition = reasoning;
        } else {
            addition = "\n\n" + reasoning;
        }
        outcome.full.append(addition);
        appendLive(addition);
        if (allowDelta) {
            callback.onReasoningAsAnswer();
            callback.onDelta(addition);
        }
        if (incomplete) {
            callback.onIncomplete();
        }
        return Attempt.success(outcome.full.toString(), "");
    }

    // ------------------------------------------------------------------
    // SSE 流式读取：按事件而不是按整段响应工作。
    // ------------------------------------------------------------------

    /** 一次流式请求的累积结果。 */
    private static final class StreamOutcome {
        final StringBuilder full = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        /** 续写前的已输出内容长度，用于区分“本次新产出”。 */
        final int baseLength;
        /** 续写时用于去掉与已输出内容重复的开头；首次请求为 null。 */
        final ContinuationFilter filter;
        String error;
        boolean cancelled;
        boolean streamStarted;
        /** 是否收到了正常的结束标记（[DONE] 或 finish_reason）。 */
        boolean completed;
        /** finish_reason 为 length / max_tokens：内容被输出上限截断。 */
        boolean truncated;
        int events;

        StreamOutcome(String continuationPrefix) {
            String prefix = continuationPrefix == null ? "" : continuationPrefix;
            baseLength = prefix.length();
            if (!prefix.isEmpty()) {
                full.append(prefix);
                filter = new ContinuationFilter(prefix);
            } else {
                filter = null;
            }
        }

        boolean hasNewContent() {
            return full.length() > baseLength;
        }
    }

    /**
     * 续写去重：模型有时会从上一段结尾重复几个字，有时干脆把整段重新输出一遍。
     * 开头先缓冲几十个字符，等能判断重复后再一次性放行，之后恢复逐字推送；
     * 判定为“从头重写”时，继续按已有内容逐字对齐跳过，直到出现真正的新内容。
     */
    private static final class ContinuationFilter {
        private static final int HOLD_CHARS = 96;
        /** 开头连续 40 个字符与已有内容相同，就认为是整段重写。 */
        private static final int RESTART_MIN = 40;
        private final String prefix;
        private final StringBuilder pending = new StringBuilder();
        private boolean released;
        /** >=0 表示正在跳过“整段重写”的开头，值是已经对齐到 prefix 的位置。 */
        private int skipPos = -1;

        ContinuationFilter(String prefix) {
            this.prefix = prefix == null ? "" : prefix;
        }

        String accept(String delta) {
            if (delta == null || delta.isEmpty()) {
                return "";
            }
            if (!released) {
                pending.append(delta);
                if (pending.length() < HOLD_CHARS) {
                    return "";
                }
                return release();
            }
            return stripReplay(delta);
        }

        String finish() {
            if (released) {
                return "";
            }
            return release();
        }

        private String release() {
            released = true;
            String pendingText = pending.toString();
            pending.setLength(0);
            int common = 0;
            int limit = Math.min(RESTART_MIN + 160,
                    Math.min(prefix.length(), pendingText.length()));
            while (common < limit && prefix.charAt(common) == pendingText.charAt(common)) {
                common++;
            }
            if (common >= RESTART_MIN) {
                // 模型把整段回答重新输出：先跳过开头，后续继续按原内容对齐。
                skipPos = common;
                return "";
            }
            return trimSuffixOverlap(prefix, pendingText);
        }

        /** 对齐跳过“模型重写”的那一段：一旦内容与已有回答分叉，分叉点就是真正的新内容。 */
        private String stripReplay(String delta) {
            if (skipPos < 0) {
                return delta;
            }
            if (skipPos >= prefix.length()) {
                skipPos = -1;
                return delta;
            }
            int compare = Math.min(delta.length(), prefix.length() - skipPos);
            int match = 0;
            while (match < compare && delta.charAt(match) == prefix.charAt(skipPos + match)) {
                match++;
            }
            skipPos += match;
            if (match < compare || match < delta.length()) {
                // 分叉出现：从这里开始就是新的正文。
                skipPos = -1;
                return delta.substring(match);
            }
            return "";
        }
    }

    /** 去掉续写内容里与已输出内容尾部重叠的开头。 */
    private static String trimSuffixOverlap(String prefix, String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        if (prefix == null || prefix.isEmpty()) {
            return delta;
        }
        String out = delta;
        int max = Math.min(400, Math.min(prefix.length(), out.length()));
        for (int len = max; len >= 6; len--) {
            if (prefix.regionMatches(prefix.length() - len, out, 0, len)) {
                out = out.substring(len);
                break;
            }
        }
        // 已输出内容以空白结尾时，避免续写开头多出一个空行。
        int skip = 0;
        while (skip < out.length() && Character.isWhitespace(out.charAt(skip))) {
            skip++;
        }
        if (skip > 0 && Character.isWhitespace(prefix.charAt(prefix.length() - 1))) {
            out = out.substring(skip);
        }
        return out;
    }

    /** 把续写缓冲里最后不足 HOLD_CHARS 的字符补发出去。 */
    private void flushContinuationFilter(Callback callback, boolean allowDelta,
                                         StreamOutcome outcome) {
        if (outcome.filter == null) {
            return;
        }
        String rest = outcome.filter.finish();
        if (rest.isEmpty()) {
            return;
        }
        outcome.full.append(rest);
        appendLive(rest);
        if (allowDelta) {
            callback.onDelta(rest);
        }
        outcome.events++;
    }

    /**
     * 读取 text/event-stream 响应。每读到一条完整事件就立刻解析并回调，
     * 绝不等待整个响应结束。这正是“实时”的关键。
     */
    private void readSse(InputStream input, long myGeneration, Callback callback,
                         boolean allowDelta, StreamOutcome outcome, StringBuilder raw) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        // SSE 允许一条事件由多行 data: 组成，用空行分隔。
        java.util.List<String> dataLines = new java.util.ArrayList<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (generation.get() != myGeneration) {
                    outcome.cancelled = true;
                    return;
                }
                // 只在还没确认是 SSE 之前缓存原文，用于 Content-Type 缺失时回退解析；
                // 一旦解析出内容就不再占用额外内存。
                if (outcome.events == 0 && raw.length() < MAX_WHOLE_BODY_BYTES) {
                    raw.append(line).append('\n');
                }
                if (line.isEmpty()) {
                    if (!dispatchEvent(dataLines, callback, allowDelta, outcome,
                            myGeneration)) {
                        return;
                    }
                    continue;
                }
                char first = line.charAt(0);
                if (first == ':') {
                    // 注释/心跳，保持连接用
                    continue;
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5);
                    if (data.startsWith(" ")) {
                        data = data.substring(1);
                    }
                    dataLines.add(data);
                    // 标准 SSE 用空行分隔事件；少数网关只用单个换行。
                    // 单行已经是完整 JSON 时立刻分发，避免一直等到下一个空行。
                    if (dataLines.size() == 1 && isCompleteJson(data)) {
                        if (!dispatchEvent(dataLines, callback, allowDelta, outcome,
                                myGeneration)) {
                            return;
                        }
                    }
                } else if (line.startsWith("event:")) {
                    // OpenAI 兼容接口不使用命名事件；这里只记录不中断。
                    String event = line.substring(6).trim();
                    if ("error".equals(event) && dataLines.isEmpty()) {
                        // 错误事件的数据行随后到达
                        continue;
                    }
                }
                // id: / retry: / 其它字段忽略
            }
            // 部分服务端在最后一个事件后直接断开，不补空行。
            if (!dataLines.isEmpty()) {
                dispatchEvent(dataLines, callback, allowDelta, outcome, myGeneration);
            }
        } catch (SocketTimeoutException e) {
            if (generation.get() != myGeneration) {
                outcome.cancelled = true;
            } else {
                outcome.error = "连接超时，请检查网络后重试。";
            }
        } catch (Exception e) {
            if (generation.get() != myGeneration) {
                outcome.cancelled = true;
            } else {
                String detail = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                outcome.error = "网络连接中断：" + trim(detail, 160);
            }
        }
    }

    /** 分发一条完整 SSE 事件；返回 false 表示停止读取（[DONE] 或错误）。 */
    private boolean dispatchEvent(java.util.List<String> dataLines, Callback callback,
                                  boolean allowDelta, StreamOutcome outcome,
                                  long myGeneration) {
        if (dataLines.isEmpty()) {
            return true;
        }
        String[] lines = dataLines.toArray(new String[0]);
        dataLines.clear();
        String joined = joinLines(lines, "\n").trim();
        if (joined.isEmpty()) {
            return true;
        }
        if ("[DONE]".equals(joined)) {
            outcome.completed = true;
            return false;
        }
        if (!outcome.streamStarted) {
            outcome.streamStarted = true;
            callback.onStreamStart();
        }
        boolean parsed = parseEventPayload(joined, callback, allowDelta, outcome);
        if (!parsed && lines.length > 1) {
            // 兼容非标准服务端：没有空行分隔、每行都是独立 JSON。
            for (String single : lines) {
                String data = single.trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                if (!parseEventPayload(data, callback, allowDelta, outcome)) {
                    break;
                }
            }
        }
        if (outcome.error != null && !outcome.error.isEmpty()) {
            return false;
        }
        return generation.get() == myGeneration;
    }

    private static String joinLines(String[] lines, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    /** 判断一行 data 是否已经是完整 JSON（用于无空行分隔的服务端也能即时分发）。 */
    private static boolean isCompleteJson(String data) {
        if (data == null) {
            return false;
        }
        String value = data.trim();
        if (value.length() < 2 || "[DONE]".equals(value)) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    /**
     * 解析一条 JSON 事件，把 content / reasoning_content 增量立刻回调。
     * 返回 false 表示这条数据不是合法 JSON。
     */
    private boolean parseEventPayload(String data, Callback callback, boolean allowDelta,
                                      StreamOutcome outcome) {
        JSONObject event;
        try {
            event = new JSONObject(data);
        } catch (Exception e) {
            return false;
        }
        if (event.has("error") && !event.isNull("error")) {
            JSONObject error = event.optJSONObject("error");
            String message = error != null
                    ? error.optString("message", "生成失败")
                    : event.optString("error", "生成失败");
            outcome.error = "生成失败：" + trim(message, 200);
            return true;
        }
        String piece = "";
        String reasoningPiece = "";
        JSONArray choices = event.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                String finishReason = choice.optString("finish_reason", "");
                if (!finishReason.isEmpty() && !"null".equals(finishReason)) {
                    // 收到了结束原因，即使暂时没等到 [DONE] 也算本次回答完整结束；
                    // 但 length / max_tokens 表示内容被输出上限截断，不能当成完整回答。
                    outcome.completed = true;
                    String reason = finishReason.toLowerCase(java.util.Locale.ROOT);
                    if ("length".equals(reason) || "max_tokens".equals(reason)) {
                        outcome.truncated = true;
                    }
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta != null) {
                    reasoningPiece = reasoningText(delta);
                    piece = contentText(delta);
                }
                JSONObject message = choice.optJSONObject("message");
                if (message != null) {
                    // 少数网关同时给 delta 和最终 message：优先用 delta，
                    // 只有当本次事件还没有正文/思考、且之前也从未收到过对应内容时，
                    // 才从 message 补齐，避免把最终 message 的完整内容再追加一遍。
                    if (reasoningPiece.isEmpty() && outcome.reasoning.length() == 0) {
                        reasoningPiece = reasoningText(message);
                    }
                    if (piece.isEmpty()
                            && outcome.full.length() == outcome.baseLength) {
                        piece = contentText(message);
                    }
                }
            }
        }
        if (!reasoningPiece.isEmpty()) {
            outcome.reasoning.append(reasoningPiece);
            if (allowDelta) {
                callback.onReasoningDelta(reasoningPiece);
            }
        }
        if (!piece.isEmpty()) {
            String emit = outcome.filter == null ? piece : outcome.filter.accept(piece);
            if (!emit.isEmpty()) {
                outcome.full.append(emit);
                appendLive(emit);
                if (allowDelta) {
                    callback.onDelta(emit);
                }
            }
        }
        if (!piece.isEmpty() || !reasoningPiece.isEmpty()) {
            outcome.events++;
        }
        return true;
    }

    /** 非流式响应：整段读入后再解析，绝不伪造逐字动画。 */
    private void parseWholeBody(String body, Callback callback, boolean allowDelta,
                                StreamOutcome outcome) {
        if (body == null) {
            return;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        // Content-Type 标错但内容仍是 SSE 的情况（原始文本以 data: 开头）。
        if (trimmed.startsWith("data:")) {
            String[] lines = trimmed.split("\\r?\\n");
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        return;
                    }
                    parseEventPayload(data, callback, allowDelta, outcome);
                    if (outcome.error != null && !outcome.error.isEmpty()) {
                        return;
                    }
                }
            }
            return;
        }
        parseEventPayload(trimmed, callback, allowDelta, outcome);
    }


    /** 设置页“获取模型列表”回调：ids 为 /models 接口返回的 data[].id。 */
    public interface ModelsCallback {
        void onModels(List<String> ids);

        void onError(String message, int code);
    }

    /**
     * GET {base}/models，读取当前 API Key 可用的模型 id 列表。
     * 与对话请求相同的直连方式，Authorization 头带 Bearer Key。
     */
    public void listModels(final String apiKey, final String baseUrl,
                           final ModelsCallback callback) {
        Thread thread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(modelsEndpoint(baseUrl));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(30000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    String body = readAll(connection.getErrorStream(), 6000);
                    callback.onError(friendlyError(status, extractErrorMessage(body)), status);
                    return;
                }
                String body = readAll(connection.getInputStream(), 262144);
                List<String> ids = new java.util.ArrayList<>();
                try {
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data == null) {
                        // 少数 OpenAI 兼容网关把列表放在 models 字段里。
                        data = json.optJSONArray("models");
                    }
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            Object item = data.opt(i);
                            String id;
                            if (item instanceof JSONObject) {
                                id = ((JSONObject) item).optString("id", "").trim();
                            } else if (item instanceof String) {
                                id = ((String) item).trim();
                            } else {
                                continue;
                            }
                            if (!id.isEmpty() && !ids.contains(id)) {
                                ids.add(id);
                            }
                        }
                    }
                } catch (Exception e) {
                    callback.onError("接口返回的模型列表格式无法解析。", 0);
                    return;
                }
                callback.onModels(ids);
            } catch (SocketTimeoutException e) {
                callback.onError("连接超时，请检查网络后重试。", 0);
            } catch (Exception e) {
                String detail = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                callback.onError("网络连接失败：" + trim(detail, 160), 0);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception ignored) {
                        // 忽略关闭异常
                    }
                }
            }
        }, "deepseek-models");
        thread.setDaemon(true);
        thread.start();
    }

    /** 设置页“测试连接”使用的轻量请求。 */
    public void ping(final String apiKey, final String baseUrl, final String model,
                     final String fallbackModel, final Callback callback) {
        final long myGeneration = generation.incrementAndGet();
        Thread thread = new Thread(() -> {
            List<Msg> messages = new java.util.ArrayList<>();
            messages.add(new Msg("user", "ping"));
            Attempt attempt = pingOnce(myGeneration, apiKey, baseUrl, model, messages);
            if (generation.get() != myGeneration) {
                return;
            }
            if (!attempt.ok && attempt.modelError && fallbackModel != null
                    && !fallbackModel.trim().isEmpty()
                    && !fallbackModel.trim().equalsIgnoreCase(model)) {
                callback.onModelSwitched(fallbackModel.trim());
                attempt = pingOnce(myGeneration, apiKey, baseUrl,
                        fallbackModel.trim(), messages);
            }
            if (generation.get() != myGeneration) {
                return;
            }
            if (attempt.ok) {
                callback.onDone(attempt.full);
            } else {
                callback.onError(attempt.error, attempt.code);
            }
        }, "deepseek-ping");
        thread.setDaemon(true);
        thread.start();
    }

    private Attempt pingOnce(long myGeneration, String apiKey, String baseUrl, String model,
                             List<Msg> messages) {
        HttpURLConnection connection = null;
        try {
            JSONObject payload = buildPayload(model, messages, false, false,
                    Prefs.THINK_LEVEL_HIGH, 2);
            payload.put("max_tokens", 8);
            connection = openConnection(apiKey, baseUrl, payload);
            activeConnection = connection;
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String body = readAll(connection.getErrorStream(), 6000);
                String serverMessage = extractErrorMessage(body);
                return Attempt.failure(status, friendlyError(status, serverMessage),
                        isModelError(status, serverMessage), isImageError(status, serverMessage));
            }
            String body = readAll(connection.getInputStream(), 20000);
            String content = "";
            try {
                JSONObject json = new JSONObject(body);
                JSONArray choices = json.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject message = choices.optJSONObject(0).optJSONObject("message");
                    if (message != null && message.has("content")
                            && !message.isNull("content")) {
                        content = message.optString("content", "");
                    }
                }
            } catch (Exception ignored) {
                // 非预期格式，但 HTTP 200 依然视为连通
            }
            if (content.isEmpty()) {
                content = "OK";
            }
            return Attempt.success(content, "");
        } catch (Exception e) {
            return Attempt.failure(0, "网络连接失败：" + trim(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 160),
                    false);
        } finally {
            if (activeConnection == connection) {
                activeConnection = null;
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // HTTP / JSON 工具
    // ------------------------------------------------------------------
    private HttpURLConnection openConnection(String apiKey, String baseUrl, JSONObject payload)
            throws Exception {
        URL url = new URL(endpoint(baseUrl));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        // 关键：禁止 gzip。压缩会把多条 SSE 事件攒在一个压缩块里，
        // HttpURLConnection 的解压流要等整块到齐才吐数据，实时流会变成整段。
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(bytes);
            out.flush();
        } finally {
            out.close();
        }
        return connection;
    }

    private JSONObject buildPayload(String model, List<Msg> messages, boolean stream,
                                    boolean thinking, int thinkLevel, int thinkParamMode)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        JSONArray array = new JSONArray();
        for (Msg message : messages) {
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            if (message.hasImages()) {
                // DeepSeek 视觉输入采用 OpenAI 兼容的 content block 数组：
                // [{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}]
                JSONArray blocks = new JSONArray();
                if (!message.content.isEmpty()) {
                    JSONObject textBlock = new JSONObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", message.content);
                    blocks.put(textBlock);
                }
                for (String dataUrl : message.images) {
                    JSONObject imageBlock = new JSONObject();
                    imageBlock.put("type", "image_url");
                    JSONObject imageUrl = new JSONObject();
                    imageUrl.put("url", dataUrl);
                    imageUrl.put("detail", "auto");
                    imageBlock.put("image_url", imageUrl);
                    blocks.put(imageBlock);
                }
                item.put("content", blocks);
            } else {
                item.put("content", message.content);
            }
            array.put(item);
        }
        payload.put("messages", array);
        payload.put("stream", stream);
        payload.put("temperature", 0.7);
        if (stream) {
            payload.put("max_tokens", 4096);
        }
        if (thinking && thinkParamMode < 2) {
            // DeepSeek V3.2 / OpenAI 兼容网关的原生思考参数。
            JSONObject think = new JSONObject();
            think.put("type", "enabled");
            payload.put("thinking", think);
            if (thinkParamMode < 1) {
                payload.put("reasoning_effort", Prefs.thinkLevelApiValue(thinkLevel));
            }
        }
        return payload;
    }

    /**
     * 从 delta / message 中读取正文增量。多数网关是字符串；
     * 少数返回 content block 数组（text / output_text），这里把文本段拼起来。
     * 仍兼容把正文直接放在 text / output_text 字段的网关。
     */
    private static String contentText(JSONObject object) {
        if (object == null) {
            return "";
        }
        if (object.has("content") && !object.isNull("content")) {
            String content = textFromContent(object.opt("content"));
            if (!content.isEmpty()) {
                return content;
            }
        }
        Object directText = object.opt("text");
        if (directText instanceof String) {
            return (String) directText;
        }
        Object outputText = object.opt("output_text");
        if (outputText instanceof String) {
            return (String) outputText;
        }
        return "";
    }

    /** 把 content 字段的字符串 / 内容块数组 / 对象统一转成纯文本。 */
    private static String textFromContent(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONObject) {
            JSONObject block = (JSONObject) value;
            Object text = block.opt("text");
            return text instanceof String ? (String) text : "";
        }
        if (!(value instanceof JSONArray)) {
            return "";
        }
        JSONArray blocks = (JSONArray) value;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < blocks.length(); i++) {
            Object item = blocks.opt(i);
            if (item instanceof String) {
                builder.append((String) item);
                continue;
            }
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject block = (JSONObject) item;
            String type = block.optString("type", "");
            // 只接受真正的正文块，避免把 reasoning / tool_call 等块误当回答。
            if (!type.isEmpty() && !"text".equals(type)
                    && !"output_text".equals(type) && !"input_text".equals(type)) {
                continue;
            }
            Object text = block.opt("text");
            if (text instanceof String) {
                builder.append((String) text);
            } else if (text instanceof JSONObject) {
                // 少数网关的 text 是 {"value": "..."} 结构。
                String nested = ((JSONObject) text).optString("value", "");
                if (!nested.isEmpty()) {
                    builder.append(nested);
                }
            }
        }
        return builder.toString();
    }

    /**
     * 从 delta / message 中读取思考增量。只接受字符串，
     * 避免把结构化字段误当成正文。
     */
    private static String reasoningText(JSONObject object) {
        if (object == null) {
            return "";
        }
        String[] keys = {"reasoning_content", "reasoning_text", "reasoning",
                "thinking_content", "thinking"};
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        }
        return "";
    }

    /** 参数降级判断：仅对“思考参数不被支持”的 4xx 生效，图片错误不在此列。 */
    private static boolean isThinkingParamError(Attempt attempt) {
        if (attempt == null || attempt.code != 400 && attempt.code != 422) {
            return false;
        }
        String lower = attempt.rawMessage == null ? ""
                : attempt.rawMessage.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("image") || lower.contains("vision")
                || lower.contains("multimodal")) {
            return false;
        }
        return lower.contains("thinking") || lower.contains("reasoning")
                || lower.contains("reasoning_effort");
    }

    public static String endpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.isEmpty()) {
            base = Prefs.DEFAULT_BASE_URL;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    /** 由接口地址推导 /models 端点；支持用户把地址填成 .../v1 或 .../chat/completions。 */
    public static String modelsEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.isEmpty()) {
            base = Prefs.DEFAULT_BASE_URL;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        }
        return base + "/models";
    }

    private static String readAll(InputStream input, int maxBytes) {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
                if (builder.length() > maxBytes) {
                    break;
                }
            }
        } catch (Exception ignored) {
            // 读取错误响应失败时返回已有内容
        }
        return builder.toString();
    }

    private static String extractErrorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
                return error.toString();
            }
            return json.optString("message", "");
        } catch (Exception ignored) {
            return body.trim();
        }
    }

    private static boolean isModelError(int status, String message) {
        if (status != 400 && status != 404 && status != 422) {
            return false;
        }
        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        boolean mentionsModel = lower.contains("model");
        boolean notUsable = lower.contains("not exist") || lower.contains("not found")
                || lower.contains("not_found") || lower.contains("invalid")
                || lower.contains("unsupported") || lower.contains("does not exist")
                || lower.contains("unknown") || lower.contains("no such");
        return mentionsModel && notUsable;
    }

    /** 判断 400 是否由“不支持图片输入”引起，用于自动降级为纯文本重试。 */
    private static boolean isImageError(int status, String message) {
        if (status != 400) {
            return false;
        }
        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("image") || lower.contains("vision")
                || lower.contains("multimodal") || lower.contains("multi-modal")
                || lower.contains("input_image") || lower.contains("image_url");
    }

    private static String friendlyError(int status, String serverMessage) {
        switch (status) {
            case 401:
                return "API Key 无效或已过期，请到设置中检查。";
            case 402:
                return "账户余额不足，请到 DeepSeek 平台充值后重试。";
            case 403:
                return "当前 API Key 没有访问该模型的权限。";
            case 404:
                return "接口地址不存在（HTTP 404），请检查设置中的接口地址。";
            case 429:
                return "请求过于频繁或额度已用尽，请稍后再试。";
            default:
                if (status >= 500) {
                    return "DeepSeek 服务暂时不可用（HTTP " + status + "），请稍后重试。";
                }
                if (serverMessage != null && !serverMessage.trim().isEmpty()) {
                    return "请求失败（HTTP " + status + "）：" + trim(serverMessage.trim(), 200);
                }
                return "请求失败（HTTP " + status + "），请检查设置后重试。";
        }
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static final class Attempt {
        final boolean ok;
        /** 已生成的全部正文（续写时包含续写前的前缀），失败时也不会丢。 */
        final String full;
        final String reasoning;
        final String error;
        final int code;
        final boolean modelError;
        final boolean imageError;
        /** 已经收到内容但连接中断，可以带着 full 去断点续写。 */
        final boolean interrupted;
        /** 中断原因是“没有结束标记就断开”，这种只额外续写一次。 */
        final boolean eofTruncated;
        /** 完全没有内容时的可重试错误（超时 / 网络重置 / 429 / 5xx）。 */
        final boolean retryable;
        /** 接口原始错误信息，用于判断是否属于思考参数不兼容。 */
        final String rawMessage;

        private Attempt(boolean ok, String full, String reasoning, String error, int code,
                        boolean modelError, boolean imageError, boolean interrupted,
                        boolean eofTruncated, boolean retryable, String rawMessage) {
            this.ok = ok;
            this.full = full == null ? "" : full;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.error = error;
            this.code = code;
            this.modelError = modelError;
            this.imageError = imageError;
            this.interrupted = interrupted;
            this.eofTruncated = eofTruncated;
            this.retryable = retryable;
            this.rawMessage = rawMessage == null ? "" : rawMessage;
        }

        static Attempt success(String full, String reasoning) {
            return new Attempt(true, full, reasoning, "", 200, false, false,
                    false, false, false, "");
        }

        static Attempt failure(int code, String error, boolean modelError) {
            return failure(code, error, modelError, false);
        }

        static Attempt failure(int code, String error, boolean modelError, boolean imageError) {
            return failure(code, error, modelError, imageError, "");
        }

        static Attempt failure(int code, String error, boolean modelError, boolean imageError,
                               String rawMessage) {
            boolean retryable = !modelError && !imageError
                    && (code == 0 || code == 408 || code == 429 || code >= 500);
            return new Attempt(false, "", "", error, code, modelError, imageError,
                    false, false, retryable, rawMessage);
        }

        /** 已经拿到部分内容后断开：full 必须原样保留。 */
        static Attempt interrupted(String full, String reasoning, String error, int code,
                                   boolean eofTruncated) {
            return new Attempt(false, full, reasoning, error, code, false, false,
                    true, eofTruncated, false, "");
        }

        static Attempt cancelled() {
            return new Attempt(false, "", "", "已停止生成", -2, false, false,
                    false, false, false, "");
        }
    }
}
