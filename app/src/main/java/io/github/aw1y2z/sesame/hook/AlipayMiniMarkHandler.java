package io.github.aw1y2z.sesame.hook;

import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * 支付宝小程序标记处理器（对应原Kotlin的AlipayMiniMarkHandler）。
 *
 * <p>安全：该路由原先直接实现 {@code HttpHandler} 而不做任何鉴权，任何本机 App 都能调用；
 * 现在改为继承 {@link BaseHandler}，必须携带调试令牌，并且默认不注册
 * （由 {@code AppConfig.debugExtraRoutes} 控制）。
 */
public class AlipayMiniMarkHandler extends BaseHandler {

    /**
     * 构造方法（调用父类 BaseHandler 的构造器传入鉴权Token）
     *
     * @param secretToken 鉴权秘钥令牌
     */
    public AlipayMiniMarkHandler(String secretToken) {
        super(secretToken);
    }

    /**
     * 处理GET请求：解析 appid 与 version，返回小程序标记。
     */
    @Override
    protected NanoHTTPD.Response onGet(NanoHTTPD.IHTTPSession session) {
        // 获取GET请求参数（parms是NanoHTTPD解析后的参数Map）
        Map<String, String> params = session.getParms();
        String appid = params.get("appid");
        String version = params.get("version");

        // 参数验证：空值/空白字符串检查（等效Kotlin的isNullOrBlank）
        if (appid == null || appid.trim().isEmpty() || version == null || version.trim().isEmpty()) {
            return badRequest("参数缺失，请提供appid和version参数");
        }

        try {
            String miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(appid, version);
            if (miniMark != null && !miniMark.trim().isEmpty()) {
                // 值必须先转义（原先注释写着"转义双引号"其实并没有转义）
                return ok("{\"success\":true,\"alipayMiniMark\":\"" + StringUtil.escapeJson(miniMark) + "\"}");
            }
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"error\":\"获取支付宝小程序标记失败\"}");
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"error\":\"服务器内部错误: " + StringUtil.escapeJson(errorMsg) + "\"}");
        }
    }
}
