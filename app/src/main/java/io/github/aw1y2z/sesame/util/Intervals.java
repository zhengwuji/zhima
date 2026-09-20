package io.github.aw1y2z.sesame.util;

/**
 * 全模块共享的等待/重试间隔常量。
 *
 * <p>背景：原先 {@code Thread.sleep(600)}、{@code Thread.sleep(750)}、{@code Thread.sleep(1500)}
 * 这类裸数字散落在 40 多处业务循环里（RPC 桥、庄园砸蛋、抢能量、答题上报等），
 * 调参要全文搜，且看不出某个数字为什么是这个值。这里把**跨模块复用**的那几个收敛成命名常量，
 * 后续统一调参只改这一个文件；与具体业务强相关的间隔（如"等界面稳定""行走时长"）仍留在原处。
 */
public final class Intervals {

    private Intervals() {
    }

    /** RPC 请求之间的基础间隔（毫秒）：原值 600，配合 {@link RandomUtil#delay()} 抖动 */
    public static final int RPC_BASE_DELAY_MS = 600;

    /** RPC 失败重试的默认间隔（毫秒）：调用方未显式指定 retryInterval 时使用 */
    public static final int RPC_RETRY_DELAY_MS = 1000;

    /** 任务循环中"下一轮再跑"的基础等待（毫秒）：原值 750 */
    public static final int TASK_LOOP_DELAY_MS = 750;

    /** RPC 打点/接口失败后的短退避（毫秒） */
    public static final int SHORT_BACKOFF_MS = 200;

    /** 界面/页面切换后的稳定等待（毫秒）：原值 500 */
    public static final int UI_SETTLE_MS = 500;

    /** 答题提交后的等待（毫秒）：原值 1500 */
    public static final int ANSWER_SUBMIT_WAIT_MS = 1500;
}
