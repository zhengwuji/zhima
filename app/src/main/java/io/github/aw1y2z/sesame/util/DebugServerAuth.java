package io.github.aw1y2z.sesame.util;

import java.io.File;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 本地调试 HTTP 服务的凭据与端口管理。
 *
 * <p>背景：早期版本把鉴权令牌硬编码在源码里（{@code ET3vB^#...}），源码公开即等于没有鉴权；
 * 端口也固定 8080，容易被本机其它 App 扫描到。现在改为：
 * <ul>
 *     <li>令牌：首次使用时用 {@link SecureRandom} 生成 32 位十六进制随机串，落盘到模块主目录
 *     {@code debug_server.txt}，不再出现在源码里；</li>
 *     <li>端口：配置为 0（默认）时随机取 20000~40000 之间的端口，并把实际端口一并落盘；</li>
 *     <li>服务本身仍只监听 127.0.0.1，且默认不启动（见 {@code AppConfig.debugHttpServer}）。</li>
 * </ul>
 *
 * <p>凭据文件由模块 App 进程与注入进程共享，用户可直接用文件管理器查看，
 * 从电脑访问时用 {@code adb forward tcp:<port> tcp:<port>}。
 */
public final class DebugServerAuth {

    private static final String TAG = DebugServerAuth.class.getSimpleName();

    /** 凭据文件名（位于模块主目录） */
    public static final String CREDENTIAL_FILE_NAME = "debug_server.txt";

    /** 令牌随机字节数：16 字节 → 32 位十六进制字符 */
    private static final int TOKEN_BYTES = 16;

    private static final int PORT_MIN = 20000;
    private static final int PORT_MAX = 40000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String cachedToken;
    private static int cachedPort;

    private DebugServerAuth() {
    }

    /** 凭据文件（模块主目录下，随配置一起被用户看到） */
    public static File getCredentialFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, CREDENTIAL_FILE_NAME);
    }

    /**
     * 读取（不存在时生成）调试令牌。
     * <p>任何异常都会退化为进程内随机令牌，绝不回退到固定字符串。
     */
    public static synchronized String getOrCreateToken() {
        if (!StringUtil.isEmpty(cachedToken)) {
            return cachedToken;
        }
        String token = null;
        try {
            token = readValue("token");
        } catch (Throwable t) {
            Log.debug("读取调试令牌失败: " + t);
        }
        if (StringUtil.isEmpty(token)) {
            token = generateToken();
            Log.record("已生成新的调试服务令牌（见 " + getCredentialFile().getAbsolutePath() + "）");
        }
        cachedToken = token;
        return cachedToken;
    }

    /**
     * 解析实际监听端口：配置了 1~65535 就用配置值，否则随机取 20000~40000。
     */
    public static synchronized int resolvePort(int configuredPort) {
        int port;
        if (configuredPort > 0 && configuredPort <= 65535) {
            port = configuredPort;
        } else {
            port = PORT_MIN + RANDOM.nextInt(PORT_MAX - PORT_MIN + 1);
        }
        cachedPort = port;
        return port;
    }

    /**
     * 把令牌与实际端口写入凭据文件，方便用户查询（服务本身不打日志，避免日志泄露令牌）。
     */
    public static synchronized void publishCredentials(String token, int port) {
        cachedToken = token;
        cachedPort = port;
        StringBuilder sb = new StringBuilder();
        sb.append("# Sesame-M 本地调试 HTTP 服务凭据（模块自动生成，可删除；删除后会重新生成）\n");
        sb.append("# 仅监听 127.0.0.1，且需要 AppConfig.debugHttpServer 开启后才会启动\n");
        sb.append("# 用法：Authorization: Bearer <token>（也支持直接放 <token>）\n");
        sb.append("# 从电脑访问：adb forward tcp:").append(port).append(" tcp:").append(port).append('\n');
        sb.append("token=").append(token).append('\n');
        sb.append("port=").append(port).append('\n');
        if (!FileUtil.write2File(sb.toString(), getCredentialFile())) {
            Log.debug("调试服务凭据写入失败: " + getCredentialFile().getAbsolutePath());
        }
    }

    /** 已发布的端口（尚未启动服务时为上次解析值） */
    public static synchronized int getPublishedPort() {
        if (cachedPort > 0) {
            return cachedPort;
        }
        String port = readValue("port");
        if (!StringUtil.isEmpty(port)) {
            try {
                cachedPort = Integer.parseInt(port.trim());
            } catch (NumberFormatException ignored) {
                // 文件被手改坏时按"未知"处理，不影响服务启动
            }
        }
        return cachedPort;
    }

    /** 供 UI 展示用：形如 {@code 127.0.0.1:端口}；未知时返回占位串 */
    public static String describeEndpoint() {
        int port = getPublishedPort();
        return port > 0 ? ("127.0.0.1:" + port) : "未启动";
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TOKEN_BYTES * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    /** 读取凭据文件里的 {@code key=value}（第一个匹配项） */
    private static String readValue(String key) {
        File file = getCredentialFile();
        if (!file.exists()) {
            return null;
        }
        String content = FileUtil.readFromFile(file);
        if (StringUtil.isEmpty(content)) {
            return null;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int index = trimmed.indexOf('=');
            if (index <= 0) {
                continue;
            }
            if (key.equals(trimmed.substring(0, index).trim())) {
                return trimmed.substring(index + 1).trim();
            }
        }
        return null;
    }
}
