package come.ezlippi.isolation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 度量执行的命令数
 */
public class IsolationCommandMetrics {

    private static final Map<String, IsolationCommandMetrics> metrics = new ConcurrentHashMap<String, IsolationCommandMetrics>();


    private final IsolationCommandProperties properties;

    private final String key;

    private final String threadPoolKey;

    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();


    public IsolationCommandMetrics(String key, String threadPoolKey, IsolationCommandProperties properties) {
        this.key = key;
        this.threadPoolKey = threadPoolKey;
        this.properties = properties;
    }

    /**
     * Get or create the {@link IsolationCommandMetrics} instance for a given String.
     * <p>
     * This is thread-safe and ensures only 1 {@link IsolationCommandMetrics} per String.
     *
     * @param key        String of {@link IsolationCommand} instance requesting the {@link IsolationCommandMetrics}
     * @param properties Pass-thru to {@link IsolationCommandMetrics} instance on first time when constructed
     * @return {@link IsolationCommandMetrics}
     */
    public static IsolationCommandMetrics getInstance(String key, IsolationCommandProperties properties) {
        return getInstance(key, null, properties);
    }

    /**
     * Get or create the {@link IsolationCommandMetrics} instance for a given String.
     * <p>
     * This is thread-safe and ensures only 1 {@link IsolationCommandMetrics} per String.
     *
     * @param key        String of {@link IsolationCommand} instance requesting the {@link IsolationCommandMetrics}
     * @param properties Pass-thru to {@link IsolationCommandMetrics} instance on first time when constructed
     * @return {@link IsolationCommandMetrics}
     */
    public static IsolationCommandMetrics getInstance(String key, String threadPoolKey, IsolationCommandProperties properties) {
        // attempt to retrieve from cache first
        IsolationCommandMetrics commandMetrics = metrics.get(key);
        if (commandMetrics != null) {
            return commandMetrics;
        } else {
            synchronized (IsolationCommandMetrics.class) {
                IsolationCommandMetrics existingMetrics = metrics.get(key);
                if (existingMetrics != null) {
                    return existingMetrics;
                } else {
                    IsolationCommandMetrics newCommandMetrics = new IsolationCommandMetrics(key, threadPoolKey, properties);
                    metrics.putIfAbsent(key, newCommandMetrics);
                    return newCommandMetrics;
                }
            }
        }
    }

    void markCommandStart() {
        concurrentExecutionCount.incrementAndGet();
    }

    void markCommandDone() {
        concurrentExecutionCount.decrementAndGet();
    }

    static void reset() {
        metrics.clear();
    }

    public IsolationCommandProperties getProperties() {
        return properties;
    }

    public String getKey() {
        return key;
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    public AtomicInteger getConcurrentExecutionCount() {
        return concurrentExecutionCount;
    }

    public int getCurrentConcurrentExecutionCount() {
        return concurrentExecutionCount.get();
    }
}
