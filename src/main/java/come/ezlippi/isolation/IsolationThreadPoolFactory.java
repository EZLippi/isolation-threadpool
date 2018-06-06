package come.ezlippi.isolation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 线程池工厂,用于创建线程池,并保存创建过的线程池
 */
public class IsolationThreadPoolFactory {
    private static final Map<String, IsolationThreadPool> THREAD_POOLS = new ConcurrentHashMap<>();

    static IsolationThreadPool getInstance(String threadPoolKey, IsolationThreadPoolProperties.Builder propertiesBuilder) {

        // this should find it for all but the first time
        IsolationThreadPool previouslyCached = THREAD_POOLS.get(threadPoolKey);
        if (previouslyCached != null) {
            return previouslyCached;
        }

        // if we get here this is the first time so we need to initialize
        synchronized (IsolationThreadPoolFactory.class) {
            if (!THREAD_POOLS.containsKey(threadPoolKey)) {
                THREAD_POOLS.put(threadPoolKey, new DefaultIsolationThreadPool(threadPoolKey, propertiesBuilder));
            }
        }
        return THREAD_POOLS.get(threadPoolKey);
    }

    static int getThreadPoolCount() {
        return THREAD_POOLS.size();
    }

    static synchronized void shutdown() {
        for (IsolationThreadPool pool : THREAD_POOLS.values()) {
            pool.getExecutor().shutdown();
        }
        THREAD_POOLS.clear();
    }


    static synchronized void shutdown(long timeout, TimeUnit unit) {
        for (IsolationThreadPool pool : THREAD_POOLS.values()) {
            pool.getExecutor().shutdown();
        }
        for (IsolationThreadPool pool : THREAD_POOLS.values()) {
            try {
                while (!pool.getExecutor().awaitTermination(timeout, unit)) {
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for thread-pools to terminate. " +
                        "Pools may not be correctly shutdown or cleared.", e);
            }
        }
        THREAD_POOLS.clear();
    }
}
