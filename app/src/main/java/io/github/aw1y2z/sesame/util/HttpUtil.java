package io.github.aw1y2z.sesame.util;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 轻量 HTTP 工具：全模块复用同一个 {@link OkHttpClient}（连接池与线程池共享）。
 *
 * <p>原先 {@code GameTask} 里两处 {@code HttpURLConnection} 手写收发：没有连接复用、
 * 超时靠默认值、异常路径要靠 finally 记得 disconnect。这里统一收敛成一次调用，
 * 并把 HTTP 状态码与响应体一起返回，调用方自己判业务码。
 */
public final class HttpUtil {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private HttpUtil() {
    }

    /**
     * 发送 JSON POST 请求。
     *
     * @param url      目标地址
     * @param jsonBody 请求体（JSON 文本）
     * @param headers  附加请求头，可为 null
     * @return 结果对象；网络异常时 code = -1、body 为空串（不抛异常，调用方按失败处理）
     */
    public static Result postJson(String url, String jsonBody, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON));
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            return new Result(response.code(), responseBody == null ? "" : responseBody.string());
        } catch (IOException e) {
            return new Result(-1, "");
        } catch (Throwable t) {
            return new Result(-1, "");
        }
    }

    /** HTTP 响应结果（状态码 + 文本响应体） */
    public static final class Result {
        private final int code;
        private final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }

        public int getCode() {
            return code;
        }

        public String getBody() {
            return body;
        }

        /** HTTP 层是否成功（业务码仍需调用方解析 body 判断） */
        public boolean isHttpOk() {
            return code >= 200 && code <= 299;
        }
    }
}
