package io.github.aw1y2z.sesame.model.task.antGame;

import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.HttpUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.TaskExecutor;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 游戏任务上报工具类
 * 对应原Kotlin的GameTask枚举类
 *
 * <p><b>安全说明</b>：原实现会把 {@code AuthCodeHelper.getAuthCode(appId)} 的结果作为
 * 请求体 {@code code} 字段、并把 {@code alipayMiniMark} 作为请求头发给第三方游戏服
 * （gamesapi2.aslk2018.com）。这两个值在当前支付宝版本上恒为空，但一旦宿主恢复该能力，
 * 就等于把该小程序的 OAuth2 授权码送给第三方。现已彻底移除这两个字段与授权码获取逻辑。
 *
 * <p><b>性能说明</b>：合并了原先两处几乎重复的 HttpURLConnection 收发代码，统一走
 * {@link HttpUtil}（OkHttp 连接池复用），随机数改用 {@link ThreadLocalRandom}，
 * 同一批次复用登录 Token（失败时再强制重新登录一次）。
 */
public enum GameTask {

    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),
    Forest_slxcc("森林小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "lianyun_senlin_leyuan", "1.0.1", 3),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    private static final String LOGIN_URL = "https://gamesapi2.aslk2018.com/v2/game/login";
    private static final String REPORT_URL = "https://gamesapi2.aslk2018.com/v2/zfb/taskReport";

    /** 随机间隔下限/上限（毫秒）：原实现为 1000~3000ms */
    private static final int SLEEP_MIN_MS = 1000;
    private static final int SLEEP_RANGE_MS = 2000;

    private final String title;
    private final String appId;
    private final String gid;
    private final String action;
    private final String channel;
    private final String version;
    private final int requestsPerEgg; // 完成1个🥚要多少次 为了防止网络崩溃 多加1次
    private volatile String cachedToken; // 缓存登录Token

    /**
     * 根据小程序 appId 匹配游戏任务（金豆乐园游戏权益上报使用）
     */
    public static GameTask matchAppId(String appId) {
        if (appId == null || appId.isEmpty()) {
            return null;
        }
        for (GameTask task : values()) {
            if (appId.equals(task.appId)) {
                return task;
            }
        }
        return null;
    }

    public String getAppId() {
        return appId;
    }

    public String getTitle() {
        return title;
    }

    /**
     * 枚举构造方法
     */
    GameTask(String title, String appId, String gid, String action, String channel, String version, int requestsPerEgg) {
        this.title = title;
        this.appId = appId;
        this.gid = gid;
        this.action = action;
        this.channel = channel;
        this.version = version;
        this.requestsPerEgg = requestsPerEgg;
    }

    /**
     * 取登录 Token：默认复用缓存，force=true 时强制重新登录。
     */
    private String ensureToken(boolean force) {
        if (!force && cachedToken != null && !cachedToken.isEmpty()) {
            return cachedToken;
        }
        cachedToken = login();
        return cachedToken;
    }

    /**
     * 第一步：登录获取 Token 并缓存。
     * <p>请求体只保留游戏服自己需要的字段（v/reqId/pf/gid/version），不再携带授权码。
     */
    private String login() {
        try {
            String reqId = System.currentTimeMillis() + "_" + (ThreadLocalRandom.current().nextInt(350) + 1);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("v", version);
            bodyJson.put("pf", "zfb");
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("version", version);

            HttpUtil.Result result = HttpUtil.postJson(LOGIN_URL, bodyJson.toString(), commonHeaders(""));

            JSONObject resJson = new JSONObject(result.getBody());
            if (resJson.optInt("code") == 1) {
                JSONObject data = resJson.optJSONObject("data");
                if (data != null) {
                    String token = data.optString("token");
                    Log.record("登录成功✅Token已获取");
                    return token;
                }
            }
            Log.error("登录接口❌报错(Code" + result.getCode() + "):" + result.getBody());
        } catch (Exception e) {
            Log.error("登录过程🚨抛出异常:" + e.getMessage());
        }
        return null;
    }

    /**
     * 外部调用：执行上报任务
     * @param eggCount 目标蛋数量
     */
    public void report(String gameType, int eggCount) {
        int totalNeeded = eggCount * (this.requestsPerEgg + 1); // 多1次确保网络请求不会错误
        TaskExecutor.execute(() -> {
            String token = ensureToken(false);
            if (token == null || token.isEmpty()) {
                Log.error("无法获取⚠️有效的Token，放弃上报任务");
                return;
            }

            Log.record("开始执行🚀" + gameType + "游戏任务:目标" + eggCount + "个蛋，需请求" + totalNeeded + "次");
            for (int i = 1; i <= totalNeeded; i++) {
                if (!executeSingleReport(gameType, i, totalNeeded, null)) {
                    // 具体的错误原因已在 executeSingleReport 中详细输出
                    break;
                }
                if (i < totalNeeded) {
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(SLEEP_RANGE_MS) + SLEEP_MIN_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            Log.record("任务流程🏁运行结束");
        });
    }

    /**
     * 同步执行上报任务，返回成功上报次数。
     * 用于需要等待结果并回查服务端状态的场景（如金豆乐园游戏权益）。
     *
     * @param gameType 日志展示用的场景名
     * @param eggCount 目标蛋数量
     * @return 成功上报的次数，失败返回已成功的次数
     */
    public int reportSync(String gameType, int eggCount) {
        return reportSync(gameType, eggCount, null);
    }

    /**
     * 同步执行上报任务，可覆盖上报渠道。
     * <p>金豆乐园场景必须传 {@code "goldenbean"}，
     * 否则游戏服接受上报但支付宝侧权益不推进。
     *
     * @param channelOverride 非空时覆盖 action_finish_channel；为空用枚举默认渠道
     */
    public int reportSync(String gameType, int eggCount, String channelOverride) {
        if (eggCount <= 0) {
            return 0;
        }
        int requiredSuccesses = eggCount * this.requestsPerEgg;
        if (ensureToken(false) == null || cachedToken.isEmpty()) {
            Log.error("无法获取⚠️有效的Token，放弃上报任务");
            return 0;
        }

        int successfulReports = 0;
        boolean retriedWithFreshToken = false;
        for (int i = 1; i <= requiredSuccesses; i++) {
            if (!executeSingleReport(gameType, i, requiredSuccesses, channelOverride)) {
                // 首次失败时用新 Token 再试一次，避免 Token 过期导致整批任务白跑
                if (!retriedWithFreshToken) {
                    retriedWithFreshToken = true;
                    Log.record("上报失败，尝试重新登录后重试一次");
                    if (ensureToken(true) != null) {
                        i--;
                        continue;
                    }
                }
                break;
            }
            successfulReports++;
            if (i < requiredSuccesses) {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(SLEEP_RANGE_MS) + SLEEP_MIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return successfulReports;
    }

    /**
     * 执行单次上报请求
     * @param current 当前请求次数
     * @param total 总请求次数
     * @return 是否上报成功
     */
    private boolean executeSingleReport(String gameType, int current, int total) {
        return executeSingleReport(gameType, current, total, null);
    }

    private boolean executeSingleReport(String gameType, int current, int total, String channelOverride) {
        try {
            String reqId = System.currentTimeMillis() + "_" + (ThreadLocalRandom.current().nextInt(90) + 10); // 10-99随机数

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("v", version);
            bodyJson.put("version", version);
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("action_code", action);
            bodyJson.put("action_finish_channel",
                    channelOverride != null && !channelOverride.isEmpty() ? channelOverride : channel);

            HttpUtil.Result result = HttpUtil.postJson(REPORT_URL, bodyJson.toString(), commonHeaders(cachedToken));

            JSONObject resJson = new JSONObject(result.getBody());
            if (resJson.optInt("code") == 1) {
                if (current % this.requestsPerEgg == 0) {
                    Log.other("游戏进度📈" + gameType + "[" + current + "/" + total + "](达成" + (current / this.requestsPerEgg) + "个)");
                }
                return true;
            }
            Log.error("⚠️ 第 " + current + " 次上报业务失败 (HTTP " + result.getCode() + "): " + result.getBody());
            return false;
        } catch (Exception e) {
            Log.error("🚨 第 " + current + " 次请求发生异常:" + e);
            return false;
        }
    }

    /**
     * 游戏服公共请求头。
     * <p>已移除 alipayMiniMark（宿主 H5HttpUtils 在当前版本已不存在，取值恒为空）。
     */
    private Map<String, String> commonHeaders(String authorization) {
        Map<String, String> headers = new HashMap<>();
        if (authorization != null && !authorization.isEmpty()) {
            headers.put("authorization", authorization);
        }
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", getDynamicUA());
        headers.put("x-release-type", "ONLINE");
        headers.put("referer", "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/" + version + "/index.html");
        return headers;
    }

    /**
     * 获取动态User-Agent
     * @return 拼接后的UA字符串
     */
    private String getDynamicUA() {
        String systemUa = System.getProperty("http.agent");
        if (systemUa == null || systemUa.isEmpty()) {
            systemUa = "Mozilla/5.0 (Linux; Android 11)";
        }
        String alipayVer = String.valueOf(ApplicationHook.getAlipayVersion());
        return systemUa + " NebulaSDK/1.8.100112 Nebula AliApp(AP/" + alipayVer + ") AlipayClient/" + alipayVer;
    }
}
