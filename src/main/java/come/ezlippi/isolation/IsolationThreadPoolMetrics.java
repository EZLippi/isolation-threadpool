package come.ezlippi.isolation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录线程池度量数据
 */
public class IsolationThreadPoolMetrics {

    private static final Map<String, IsolationThreadPoolMetrics> METRICS = new ConcurrentHashMap<>();

    static void reset() {
        METRICS.clear();
    }

    private final String threadPoolKey;

    private final ThreadPoolExecutor threadPool;

    private final IsolationThreadPoolProperties properties;

    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();

    public IsolationThreadPoolMetrics(String threadPoolKey, ThreadPoolExecutor threadPool, IsolationThreadPoolProperties properties) {
        this.threadPoolKey = threadPoolKey;
        this.threadPool = threadPool;
        this.properties = properties;
    }

    public static IsolationThreadPoolMetrics getInstance(String key, ThreadPoolExecutor threadPool, IsolationThreadPoolProperties properties) {
        // attempt to retrieve from cache first
        IsolationThreadPoolMetrics threadPoolMetrics = METRICS.get(key);
        if (threadPoolMetrics != null) {
            return threadPoolMetrics;
        } else {
            synchronized (IsolationThreadPoolMetrics.class) {
                IsolationThreadPoolMetrics existingMetrics = METRICS.get(key);
                if (existingMetrics != null) {
                    return existingMetrics;
                } else {
                    IsolationThreadPoolMetrics newThreadPoolMetrics = new IsolationThreadPoolMetrics(key, threadPool, properties);
                    METRICS.putIfAbsent(key, newThreadPoolMetrics);
                    return newThreadPoolMetrics;
                }
            }
        }
    }

    public static IsolationThreadPoolMetrics getInstance(String key) {
        return METRICS.get(key);
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    public IsolationThreadPoolProperties getProperties() {
        return properties;
    }

    public AtomicInteger getConcurrentExecutionCount() {
        return concurrentExecutionCount;
    }

    public Number getCurrentActiveCount() {
        return threadPool.getActiveCount();
    }

    public Number getCurrentCompletedTaskCount() {
        return threadPool.getCompletedTaskCount();
    }

    public Number getCurrentCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    public Number getCurrentLargestPoolSize() {
        return threadPool.getLargestPoolSize();
    }

    public Number getCurrentMaximumPoolSize() {
        return threadPool.getMaximumPoolSize();
    }

    public Number getCurrentPoolSize() {
        return threadPool.getPoolSize();
    }

    public Number getCurrentTaskCount() {
        return threadPool.getTaskCount();
    }

    public Number getCurrentQueueSize() {
        return threadPool.getQueue().size();
    }

    public void markThreadExecution() {
        concurrentExecutionCount.incrementAndGet();
    }

    public void markThreadCompletion() {
        concurrentExecutionCount.decrementAndGet();
    }

    public void markThreadRejection() {
        concurrentExecutionCount.decrementAndGet();
    }


}
