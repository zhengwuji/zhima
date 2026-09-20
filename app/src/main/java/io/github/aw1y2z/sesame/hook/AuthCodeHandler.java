package io.github.aw1y2z.sesame.hook;

import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * OAuth2 授权码处理器（对应原Kotlin的AuthCodeHandler）。
 *
 * <p>安全：该路由返回的是宿主 OAuth2 授权码，原先不做任何鉴权即可被本机任意 App 调用；
 * 现在改为继承 {@link BaseHandler} 强制校验调试令牌，并且默认不注册
 * （由 {@code AppConfig.debugExtraRoutes} 控制）。
 *
 * <p>可用性：该路由依赖 AuthCodeHelper，它自建 Oauth2AuthCodeServiceImpl 实例、没走宿主依赖注入，
 * 实例内的 Oauth2AuthCodeFacade 恒为 null，调用必然抛 NPE（当前支付宝版本下固定返回 500）。
 */
public class AuthCodeHandler extends BaseHandler {

    /**
     * 构造方法（调用父类 BaseHandler 的构造器传入鉴权Token）
     *
     * @param secretToken 鉴权秘钥令牌
     */
    public AuthCodeHandler(String secretToken) {
        super(secretToken);
    }

    /**
     * 处理GET请求：解析 appId 参数，返回 OAuth2 授权码。
     */
    @Override
    protected NanoHTTPD.Response onGet(NanoHTTPD.IHTTPSession session) {
        // 获取GET请求参数（parms是NanoHTTPD解析后的参数Map）
        Map<String, String> params = session.getParms();
        String appId = params.get("appId");

        // 参数验证：空值/空白字符串检查（等效Kotlin的isNullOrBlank）
        if (appId == null || appId.trim().isEmpty()) {
            return badRequest("参数缺失，请提供appId参数");
        }

        try {
            String authCode = AuthCodeHelper.getAuthCode(appId);
            if (authCode != null) {
                // 拼接成功响应JSON：值必须先转义（原先注释写着"转义双引号"其实并没有转义）
                return ok("{\"success\":true,\"authCode\":\"" + StringUtil.escapeJson(authCode) + "\"}");
            }
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"error\":\"获取OAuth2授权码失败\"}");
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"error\":\"服务器内部错误: " + StringUtil.escapeJson(errorMsg) + "\"}");
        }
    }
}
