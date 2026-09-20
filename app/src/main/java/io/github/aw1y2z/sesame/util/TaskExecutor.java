package io.github.aw1y2z.sesame.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模块级共享后台线程池。
 *
 * <p>背景：{@code new Thread(...)} 原先散落在 {@code BaseModel}、{@code AnswerAI}、{@code TestRpc}、
 * {@code ApplicationHook}、{@code GameTask} 等 8 处以上：任务风暴时线程数不可控（每次都新建），
 * 线程没有名字（线上抓栈难定位），且 Runnable 里抛出的异常会被线程默认处理器直接吞掉。
 *
 * <p>这里统一为：小核心池 + 有界队列 + 命名线程 + 未捕获异常写日志 + 拒绝时回退到调用线程
 * （{@code CallerRunsPolicy}，保证任务不会静默丢失，同时形成天然背压）。
 */
public final class TaskExecutor {

    private static final String TAG = TaskExecutor.class.getSimpleName();

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 8;
    /** 有界队列：避免无限堆积把内存吃光 */
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_SECONDS = 30L;

    private static final ExecutorService POOL = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new NamedThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    private TaskExecutor() {
    }

    /** 提交一个"即发即忘"的后台任务（异常会被记录，不会中断线程） */
    public static void execute(Runnable task) {
        if (task == null) {
            return;
        }
        POOL.execute(guard(task));
    }

    /** 提交任务并拿到 Future（需要等待结果/超时控制时用） */
    public static Future<?> submit(Runnable task) {
        return POOL.submit(guard(task));
    }

    /** 包一层异常兜底：与线程池的 uncaught handler 形成双保险 */
    private static Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                Log.err(TAG, "后台任务异常:", t);
            }
        };
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Sesame-Background-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> Log.err(TAG, "线程 " + t.getName() + " 未捕获异常:", e));
            return thread;
        }
    }
}
