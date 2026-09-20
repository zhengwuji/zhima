package io.github.aw1y2z.sesame.hook;

import io.github.aw1y2z.sesame.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP服务器类（基于NanoHTTPD）
 * 对应原Kotlin的ModuleHttpServer，保留所有路由注册和请求处理逻辑
 */
public class ModuleHttpServer extends NanoHTTPD {
    private final String tag = "ModuleHttpServer";
    private final Map<String, HttpHandler> routes = new HashMap<>();
    
    /**
     * 无参构造方法（等效Kotlin默认参数：port=8080，secretToken=""）
     */
    public ModuleHttpServer() {
        this(8080, "", false, false);
    }
    
    /**
     * 仅指定端口的构造方法（等效Kotlin：secretToken=""）
     * @param port 服务器端口
     */
    public ModuleHttpServer(int port) {
        this(port, "", false, false);
    }
    
    /**
     * 全参构造方法（对应Kotlin主构造器）
     * @param port 服务器端口
     * @param secretToken 秘钥令牌
     * @deprecated 该重载不注册任何路由（服务空跑）；请使用带路由开关的构造器
     */
    @Deprecated
    public ModuleHttpServer(int port, String secretToken) {
        this(port, secretToken, false, false);
    }
    
    /**
     * 全参构造方法 + 路由开关。
     *
     * <p>安全：服务仅监听回环地址，且两条附加路由（原本不做鉴权）与任意 RPC 调试口默认都不注册，
     * 必须由用户在模块设置里显式开启（见 {@code AppConfig.debugRpcEnabled / debugExtraRoutes}）。
     *
     * @param port 服务器端口
     * @param secretToken 秘钥令牌（随机生成，见 {@code util.DebugServerAuth}）
     * @param enableDebugRpc 是否注册 /debugHandler（可执行任意宿主 RPC，风险最高）
     * @param enableExtraRoutes 是否注册 /getAlipayMiniMark 与 /getAuthCode
     */
    public ModuleHttpServer(int port, String secretToken, boolean enableDebugRpc, boolean enableExtraRoutes) {
        // 仅监听回环地址：避免同局域网内其他设备直接访问（原来绑 0.0.0.0 时，
        // /getAlipayMiniMark 与 /getAuthCode 两条路由不做鉴权，可被同网段任意设备调用）。
        // 本机调用不受影响；需要从电脑访问时用 adb forward tcp:8080 tcp:8080。
        super("127.0.0.1", port);
        if (enableDebugRpc) {
            register("/debugHandler", new DebugHandler(secretToken), "调试接口（需令牌，可执行宿主 RPC）");
        } else {
            Log.i("调试接口 /debugHandler 未注册：debugRpcEnabled=false");
        }
        if (enableExtraRoutes) {
            register("/getAlipayMiniMark", new AlipayMiniMarkHandler(secretToken), "获取支付宝小程序标记（需令牌）");
            register("/getAuthCode", new AuthCodeHandler(secretToken), "获取OAuth2授权码（需令牌，当前宿主版本下不可用）");
        } else {
            Log.i("附加路由未注册：debugExtraRoutes=false");
        }
    }
    
    /**
     * 路由注册方法（默认description为空）
     * 对应Kotlin的@Suppress("SameParameterValue") + 默认参数
     * @param path 接口路径
     * @param handler 处理器
     */
    private void register(String path, HttpHandler handler) {
        register(path, handler, "");
    }
    
    /**
     * 核心路由注册方法
     * @param path 接口路径
     * @param handler 处理器
     * @param description 接口描述
     */
    private void register(String path, HttpHandler handler, String description) {
        Log.i("Registering handler : " + path + " -> " + description);
        routes.put(path, handler);
    }
    
    /**
     * 重写NanoHTTPD的请求处理方法
     * 核心逻辑：匹配路由 -> 读取请求体 -> 调用处理器 -> 全局异常捕获
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        // 匹配路由，无匹配则返回404
        HttpHandler handler = routes.get(uri);
        if (handler == null) {
            return notFound();
        }
        
        try {
            // 仅POST/PUT请求读取Body
            String body = null;
            Method requestMethod = session.getMethod();
            if (requestMethod == Method.POST || requestMethod == Method.PUT) {
                body = getPostBodySafe(session);
            }
            // 调用处理器处理请求
            return handler.handle(session, body);
        } catch (Exception e) {
            // 全局异常捕获，防止服务器崩溃
            Log.printStackTrace("Server Error on " + uri, e);
            String errorMsg = "Internal Server Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, ServerCommon.MIME_PLAINTEXT, errorMsg);
        }
    }
    
    /**
     * 🔥【关键修复】安全读取POST/PUT请求体
     * 1. 确保读满content-length指定的字节数
     * 2. 强制UTF-8编码，避免中文乱码
     * @param session HTTP会话
     * @return 读取的请求体字符串，失败返回null
     */
    private String getPostBodySafe(IHTTPSession session) {
        // 解析Content-Length头
        String contentLengthStr = session.getHeaders().get("content-length");
        if (contentLengthStr == null) {
            return null;
        }
        
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthStr);
        } catch (NumberFormatException e) {
            Log.printStackTrace("Invalid content-length value: " + contentLengthStr, e);
            return null;
        }
        
        // 空Body直接返回空字符串
        if (contentLength == 0) {
            return "";
        }
        
        try {
            byte[] buffer = new byte[contentLength];
            DataInputStream inputStream = new DataInputStream(session.getInputStream());
            // readFully确保读满所有字节（不够则阻塞/抛异常）
            inputStream.readFully(buffer);
            // 明确指定UTF-8编码
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.printStackTrace("Failed to read body", e);
            return null;
        } catch (Exception e) {
            Log.printStackTrace("Error reading body fully", e);
            return null;
        }
    }
    
    /**
     * 返回404响应
     * @return NanoHTTPD Response对象
     */
    private Response notFound() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, ServerCommon.MIME_PLAINTEXT, "Not Found");
    }
}