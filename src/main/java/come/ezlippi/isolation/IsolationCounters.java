package come.ezlippi.isolation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局统计信息
 */
public class IsolationCounters {
    private static final AtomicInteger concurrentThreadsExecuting = new AtomicInteger(0);

    static int incrementGlobalConcurrentThreads() {
        return concurrentThreadsExecuting.incrementAndGet();
    }

    static int decrementGlobalConcurrentThreads() {
        return concurrentThreadsExecuting.decrementAndGet();
    }

    /**
     * Return the number of currently-executing  threads
     * @return number of currently-executing  threads
     */
    public static int getGlobalConcurrentThreadsExecuting() {
        return concurrentThreadsExecuting.get();
    }

    /**
     * Return the number of unique {@link IsolationThreadPool}s that have been registered
     * @return number of unique {@link IsolationThreadPool}s that have been registered
     */
    public static int getThreadPoolCount() {
        return IsolationThreadPoolFactory.getThreadPoolCount();
    }

}
