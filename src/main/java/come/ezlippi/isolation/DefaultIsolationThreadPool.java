package come.ezlippi.isolation;

import come.ezlippi.isolation.execution.IsolationScheduler;
import rx.Scheduler;
import rx.functions.Func0;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实现线程池隔离的核心部分,每个线程用来执行{@link IsolationCommand#run()}方法,不同的服务应设置不同的key来区分不同的线程池
 */
public class DefaultIsolationThreadPool implements IsolationThreadPool {
    private final IsolationThreadPoolProperties properties;

    private final ThreadPoolExecutor threadPool;

    private final IsolationThreadPoolMetrics metrics;

    private final BlockingQueue<Runnable> queue;

    private final int queueSize;

    public DefaultIsolationThreadPool(String threadPoolKey, IsolationThreadPoolProperties.Builder builder) {
        this.properties = IsolationPropertiesFactory.getThreadPoolProperties(threadPoolKey, builder);

        this.queueSize = properties.getWorkingQueueSize();

        this.threadPool = getThreadPollExecutor(threadPoolKey, properties);

        this.metrics = IsolationThreadPoolMetrics.getInstance(threadPoolKey, threadPool, properties);

        this.queue = threadPool.getQueue();
    }

    private ThreadPoolExecutor getThreadPollExecutor(String threadPoolKey, IsolationThreadPoolProperties properties) {
        final int dynamicCoreSize = properties.getCoreSize();
        final int maxCoreSize = properties.getMaxSize();
        final int keepAliveTime = properties.getKeepAliveTimeInMinutes();
        final int maxQueueSize = properties.getWorkingQueueSize();
        final BlockingQueue<Runnable> workQueue = getBlockingQueue(maxQueueSize);

        final ThreadFactory threadFactory = getThreadFactory(threadPoolKey);

        return new ThreadPoolExecutor(dynamicCoreSize, maxCoreSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);

    }

    private static ThreadFactory getThreadFactory(final String threadPoolKey) {
            return new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "isolation-" + threadPoolKey + "-" + threadNumber.incrementAndGet());
//                    thread.setDaemon(true);
                    return thread;
                }

            };
    }

    private BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
        if (maxQueueSize < 0) {
            return new LinkedBlockingQueue<>();
        } else {
            return new LinkedBlockingQueue<>(maxQueueSize);
        }
    }

    public ExecutorService getExecutor() {
        return threadPool;
    }

    @Override
    public Scheduler getScheduler() {
        //by default, interrupt underlying threads on timeout
        return getScheduler(new Func0<Boolean>() {
            @Override
            public Boolean call() {
                return true;
            }
        });
    }

    @Override
    public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread) {
        return new IsolationScheduler(this, shouldInterruptThread);
    }

    public void markThreadExecution() {
        metrics.markThreadExecution();
    }

    public void markThreadCompletion() {
        metrics.markThreadCompletion();
    }

    public void markThreadRejection() {
        metrics.markThreadRejection();
    }

    public boolean isQueueSpaceAvailable() {
        if (queueSize <= 0) {
            //无界队列
            return true;
        } else {
            return threadPool.getQueue().size() < properties.getWorkingQueueSize();
        }
    }
}
