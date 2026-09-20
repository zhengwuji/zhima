package io.github.aw1y2z.sesame.model.extensions;

import io.github.aw1y2z.sesame.util.TaskExecutor;

/**
 * 广播触发的扩展请求入口（测试/调试用）。
 *
 * <p>原实现继承 {@code Thread} 再 {@code setData(...).start()}：等价于裸线程 + 手写字段赋值
 * （既没有线程名，异常也会被默认处理器吞掉）。现在交给模块共享线程池（TaskExecutor），
 * 行为一致但线程可命名、异常有留痕、数量有上界。
 */
public class TestRpc {

    public static void start(String broadcastFun, String broadcastData, String testType) {
        TaskExecutor.execute(() -> ExtensionsHandle.handleRequest(testType, broadcastFun, broadcastData));
    }
}
