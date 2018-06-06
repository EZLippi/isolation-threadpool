package come.ezlippi.isolation.execution;

import come.ezlippi.isolation.IsolationThreadPool;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 任务调度
 */
public class IsolationScheduler extends Scheduler {
    private final Scheduler actualScheduler;
    private final IsolationThreadPool threadPool;

    public IsolationScheduler(Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.threadPool = null;
    }

    public IsolationScheduler(IsolationThreadPool threadPool) {
        this(threadPool, new Func0<Boolean>() {
            @Override
            public Boolean call() {
                return true;
            }
        });
    }

    public IsolationScheduler(IsolationThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
        this.threadPool = threadPool;
        this.actualScheduler = new ThreadPoolScheduler(threadPool, shouldInterruptThread);
    }

    @Override
    public Worker createWorker() {
        return new IsolationSchedulerWorker(actualScheduler.createWorker());
    }

    private class IsolationSchedulerWorker extends Worker {

        private final Worker worker;

        private IsolationSchedulerWorker(Worker actualWorker) {
            this.worker = actualWorker;
        }

        @Override
        public void unsubscribe() {
            worker.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return worker.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new Action0() {
                @Override
                public void call() {
                    action.call();
                }
            }, delayTime, unit);
        }

        @Override
        public Subscription schedule(Action0 action) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new Action0() {
                @Override
                public void call() {
                    action.call();
                }
            });
        }

    }

    private static class ThreadPoolScheduler extends Scheduler {

        private final IsolationThreadPool threadPool;
        private final Func0<Boolean> shouldInterruptThread;

        public ThreadPoolScheduler(IsolationThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public Worker createWorker() {
            return new ThreadPoolWorker(threadPool, shouldInterruptThread);
        }

    }

    /**
     * Purely for scheduling work on a thread-pool.
     * <p>
     * This is not natively supported by RxJava as of 0.18.0 because thread-pools
     * are contrary to sequential execution.
     * <p>
     * For the Hystrix case, each Command invocation has a single action so the concurrency
     * issue is not a problem.
     */
    private static class ThreadPoolWorker extends Worker {

        private final IsolationThreadPool threadPool;
        private final CompositeSubscription subscription = new CompositeSubscription();
        private final Func0<Boolean> shouldInterruptThread;

        public ThreadPoolWorker(IsolationThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public void unsubscribe() {
            subscription.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return subscription.isUnsubscribed();
        }

        @Override
        public Subscription schedule(final Action0 action) {
            if (subscription.isUnsubscribed()) {
                // don't schedule, we are unsubscribed
                return Subscriptions.unsubscribed();
            }

            ScheduledAction sa = new ScheduledAction(action);

            //
            subscription.add(sa);
            sa.addParent(subscription);

            ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool.getExecutor();
            FutureTask<?> f = (FutureTask<?>) executor.submit(sa);
            sa.add(new FutureCompleterWithConfigurableInterrupt(f, shouldInterruptThread, executor));

            return sa;
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            throw new IllegalStateException("Hystrix does not support delayed scheduling");
        }
    }

    /**
     * Very similar to rx.internal.schedulers.ScheduledAction.FutureCompleter, but with configurable interrupt behavior
     */
    private static class FutureCompleterWithConfigurableInterrupt implements Subscription {
        private final FutureTask<?> f;
        private final Func0<Boolean> shouldInterruptThread;
        private final ThreadPoolExecutor executor;

        private FutureCompleterWithConfigurableInterrupt(FutureTask<?> f, Func0<Boolean> shouldInterruptThread, ThreadPoolExecutor executor) {
            this.f = f;
            this.shouldInterruptThread = shouldInterruptThread;
            this.executor = executor;
        }

        @Override
        public void unsubscribe() {
            executor.remove(f);
            if (shouldInterruptThread.call()) {
                f.cancel(true);
            } else {
                f.cancel(false);
            }
        }

        @Override
        public boolean isUnsubscribed() {
            return f.isCancelled();
        }
    }

}
