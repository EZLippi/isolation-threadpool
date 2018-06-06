package come.ezlippi.isolation.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定时器
 */
public class IsolationTimer {
    private static final Logger logger = LoggerFactory.getLogger(IsolationTimer.class);

    private static IsolationTimer INSTANCE = new IsolationTimer();

    private IsolationTimer() {
    }


    public static IsolationTimer getInstance() {
        return INSTANCE;
    }

    /**
     * Clears all listeners.
     */
    public static void reset() {
        ScheduledExecutor ex = INSTANCE.executor.getAndSet(null);
        if (ex != null && ex.getThreadPool() != null) {
            ex.getThreadPool().shutdownNow();
        }
    }

    AtomicReference<ScheduledExecutor> executor = new AtomicReference<ScheduledExecutor>();

    /**
     * Add a {@link TimerListener} that will be executed until it is garbage collected or removed by clearing the returned {@link Reference}.
     * <p>
     * NOTE: It is the responsibility of code that adds a listener via this method to clear this listener when completed.
     * <p>
     * <blockquote>
     * <p>
     * <pre> {@code
     * // add a TimerListener
     * Reference<TimerListener> listener = IsolationTimer.getInstance().addTimerListener(listenerImpl);
     *
     * // sometime later, often in a thread shutdown, request cleanup, servlet filter or something similar the listener must be shutdown via the clear() method
     * listener.clear();
     * }</pre>
     * </blockquote>
     *
     * @param listener TimerListener implementation that will be triggered according to its <code>getIntervalTimeInMilliseconds()</code> method implementation.
     * @return reference to the TimerListener that allows cleanup via the <code>clear()</code> method
     */
    public Reference<TimerListener> addTimerListener(final TimerListener listener) {
        startThreadIfNeeded();
        // add the listener

        Runnable r = new Runnable() {

            @Override
            public void run() {
                try {
                    listener.tick();
                } catch (Exception e) {
                    logger.error("Failed while ticking TimerListener", e);
                }
            }
        };

        ScheduledFuture<?> f = executor.get().getThreadPool().scheduleAtFixedRate(r, listener.getIntervalTimeInMilliseconds(), listener.getIntervalTimeInMilliseconds(), TimeUnit.MILLISECONDS);
        return new TimerReference(listener, f);
    }

    private static class TimerReference extends SoftReference<TimerListener> {

        private final ScheduledFuture<?> f;

        TimerReference(TimerListener referent, ScheduledFuture<?> f) {
            super(referent);
            this.f = f;
        }

        @Override
        public void clear() {
            super.clear();
            // stop this ScheduledFuture from any further executions
            f.cancel(false);
        }

    }

    /**
     * Since we allow resetting the timer (shutting down the thread) we need to lazily re-start it if it starts being used again.
     * <p>
     * This does the lazy initialization and start of the thread in a thread-safe manner while having little cost the rest of the time.
     */
    protected void startThreadIfNeeded() {
        // create and start thread if one doesn't exist
        while (executor.get() == null || !executor.get().isInitialized()) {
            if (executor.compareAndSet(null, new ScheduledExecutor())) {
                // initialize the executor that we 'won' setting
                executor.get().initialize();
            }
        }
    }

    static class ScheduledExecutor {
        volatile ScheduledThreadPoolExecutor executor;
        private volatile boolean initialized;

        /**
         * We want this only done once when created in compareAndSet so use an initialize method
         */
        public void initialize() {


            ThreadFactory threadFactory = new ThreadFactory() {
                final AtomicInteger counter = new AtomicInteger();
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "IsolationTimer-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }

            };

            executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), threadFactory);
            initialized = true;
        }

        public ScheduledThreadPoolExecutor getThreadPool() {
            return executor;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    public static interface TimerListener {

        /**
         * The 'tick' is called each time the interval occurs.
         * <p>
         * This method should NOT block or do any work but instead fire its work asynchronously to perform on another thread otherwise it will prevent the Timer from functioning.
         * <p>
         * This contract is used to keep this implementation single-threaded and simplistic.
         * <p>
         * If you need a ThreadLocal set, you can store the state in the TimerListener, then when tick() is called, set the ThreadLocal to your desired value.
         */
        public void tick();

        /**
         * How often this TimerListener should 'tick' defined in milliseconds.
         */
        public int getIntervalTimeInMilliseconds();
    }

}

