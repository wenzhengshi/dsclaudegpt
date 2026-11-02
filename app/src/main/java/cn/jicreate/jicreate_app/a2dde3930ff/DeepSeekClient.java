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
    private static final int READ_TIMEOUT_MS = 90000;

    private final AtomicLong generation = new AtomicLong(0);
    private volatile HttpURLConnection activeConnection;

    public void cancel() {
        generation.incrementAndGet();
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
        final long myGeneration = generation.incrementAndGet();
        Thread thread = new Thread(() -> runStream(myGeneration, apiKey, baseUrl, model,
                fallbackModel, messages, callback), "deepseek-stream");
        thread.setDaemon(true);
        thread.start();
    }

    private void runStream(long myGeneration, String apiKey, String baseUrl, String model,
                           String fallbackModel, List<Msg> messages, Callback callback) {
        List<Msg> current = messages;
        Attempt attempt = attemptStream(myGeneration, apiKey, baseUrl, model, current,
                callback, true);
        if (generation.get() != myGeneration) {
            return;
        }
        if (!attempt.ok && attempt.imageError && hasImages(current)) {
            // 当前模型不接受图片（例如用户把模型改成了纯文本模型），
            // 去掉图片后用同一模型重试，保证对话仍能继续。
            callback.onImagesDropped();
            current = withoutImages(current);
            attempt = attemptStream(myGeneration, apiKey, baseUrl, model, current,
                    callback, true);
            if (generation.get() != myGeneration) {
                return;
            }
        }
        if (attempt.ok) {
            callback.onDone(attempt.full);
            return;
        }
        if (attempt.modelError && fallbackModel != null
                && !fallbackModel.trim().isEmpty()
                && !fallbackModel.trim().equalsIgnoreCase(model)) {
            callback.onModelSwitched(fallbackModel.trim());
            if (generation.get() != myGeneration) {
                return;
            }
            attempt = attemptStream(myGeneration, apiKey, baseUrl, fallbackModel.trim(),
                    current, callback, true);
            if (generation.get() != myGeneration) {
                return;
            }
            if (!attempt.ok && attempt.imageError && hasImages(current)) {
                callback.onImagesDropped();
                current = withoutImages(current);
                attempt = attemptStream(myGeneration, apiKey, baseUrl, fallbackModel.trim(),
                        current, callback, true);
                if (generation.get() != myGeneration) {
                    return;
                }
            }
            if (attempt.ok) {
                callback.onDone(attempt.full);
                return;
            }
        }
        callback.onError(attempt.error, attempt.code);
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

    private Attempt attemptStream(long myGeneration, String apiKey, String baseUrl,
                                  String model, List<Msg> messages, Callback callback,
                                  boolean allowDelta) {
        HttpURLConnection connection = null;
        try {
            JSONObject payload = buildPayload(model, messages, true);
            connection = openConnection(apiKey, baseUrl, payload);
            activeConnection = connection;
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String body = readAll(connection.getErrorStream(), 6000);
                String serverMessage = extractErrorMessage(body);
                return Attempt.failure(status, friendlyError(status, serverMessage),
                        isModelError(status, serverMessage), isImageError(status, serverMessage));
            }

            StringBuilder full = new StringBuilder();
            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (generation.get() != myGeneration) {
                    return Attempt.cancelled();
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("data:")) {
                    line = line.substring(5).trim();
                }
                if (line.equals("[DONE]")) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                String piece = null;
                String streamError = null;
                try {
                    JSONObject event = new JSONObject(line);
                    if (event.has("error")) {
                        JSONObject error = event.optJSONObject("error");
                        streamError = error != null
                                ? error.optString("message", "生成失败")
                                : event.optString("error", "生成失败");
                    } else {
                        JSONArray choices = event.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.optJSONObject(0);
                            if (choice != null) {
                                JSONObject delta = choice.optJSONObject("delta");
                                if (delta != null && delta.has("content")
                                        && !delta.isNull("content")) {
                                    piece = delta.optString("content", "");
                                }
                                if ((piece == null || piece.isEmpty())
                                        && choice.has("message")) {
                                    JSONObject message = choice.optJSONObject("message");
                                    if (message != null && message.has("content")
                                            && !message.isNull("content")) {
                                        piece = message.optString("content", "");
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 非 JSON 的心跳/注释行直接忽略
                }
                if (streamError != null && !streamError.isEmpty()) {
                    return Attempt.failure(0, "生成失败：" + trim(streamError, 200), false);
                }
                if (piece != null && !piece.isEmpty()) {
                    full.append(piece);
                    if (allowDelta) {
                        callback.onDelta(piece);
                    }
                }
            }
            if (full.length() == 0) {
                return Attempt.failure(0, "模型没有返回任何内容，请稍后重试。", false);
            }
            return Attempt.success(full.toString());
        } catch (SocketTimeoutException e) {
            return Attempt.failure(0, "连接超时，请检查网络后重试。", false);
        } catch (Exception e) {
            if (generation.get() != myGeneration) {
                return Attempt.cancelled();
            }
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Attempt.failure(0, "网络连接失败：" + trim(detail, 160), false);
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
            JSONObject payload = buildPayload(model, messages, false);
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
            return Attempt.success(content);
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

    private JSONObject buildPayload(String model, List<Msg> messages, boolean stream)
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
        return payload;
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
        final String full;
        final String error;
        final int code;
        final boolean modelError;
        final boolean imageError;

        private Attempt(boolean ok, String full, String error, int code, boolean modelError,
                        boolean imageError) {
            this.ok = ok;
            this.full = full;
            this.error = error;
            this.code = code;
            this.modelError = modelError;
            this.imageError = imageError;
        }

        static Attempt success(String full) {
            return new Attempt(true, full, "", 200, false, false);
        }

        static Attempt failure(int code, String error, boolean modelError) {
            return failure(code, error, modelError, false);
        }

        static Attempt failure(int code, String error, boolean modelError, boolean imageError) {
            return new Attempt(false, "", error, code, modelError, imageError);
        }

        static Attempt cancelled() {
            return new Attempt(false, "", "已停止生成", -2, false, false);
        }
    }
}
