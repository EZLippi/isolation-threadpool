package come.ezlippi.isolation;

import come.ezlippi.isolation.exception.IsolationRuntimeException;
import come.ezlippi.isolation.hook.CommandExecutionHook;
import come.ezlippi.isolation.timer.IsolationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

import java.lang.ref.Reference;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractCommand<R> implements Invokable<R> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractCommand.class);

    protected final IsolationThreadPool threadPool;

    protected final String threadPoolKey;

    protected final IsolationCommandProperties properties;

    protected enum TimedOutStatus {
        NOT_EXECUTED, COMPLETED, TIMED_OUT
    }

    protected enum CommandState {
        NOT_STARTED, OBSERVABLE_CHAIN_CREATED, USER_CODE_EXECUTED, UNSUBSCRIBED, TERMINAL
    }

    protected enum ThreadState {
        NOT_USING_THREAD, STARTED, UNSUBSCRIBED, TERMINAL
    }

    protected final IsolationCommandMetrics metrics;

    protected final String commandKey;

    protected volatile long commandStartTimestamp = -1L;

    protected CommandExecutionHook executionHook;

    protected final AtomicReference<TimedOutStatus> isCommandTimedOut = new AtomicReference<TimedOutStatus>(TimedOutStatus.NOT_EXECUTED);

    protected AtomicReference<CommandState> commandState = new AtomicReference<CommandState>(CommandState.NOT_STARTED);
    protected AtomicReference<ThreadState> threadState = new AtomicReference<ThreadState>(ThreadState.NOT_USING_THREAD);

    protected AbstractCommand(String key, String threadPoolKey, IsolationCommandProperties.Builder commandPropertiesDefaults,
                              IsolationThreadPoolProperties.Builder threadPoolPropertiesDefaults, CommandExecutionHook executionHook) {

        this.commandKey = key;
        this.properties = IsolationPropertiesFactory.getCommandProperties(key, commandPropertiesDefaults);
        this.metrics = IsolationCommandMetrics.getInstance(commandKey, properties);
        this.threadPoolKey = threadPoolKey;
        this.threadPool = IsolationThreadPoolFactory.getInstance(this.threadPoolKey, threadPoolPropertiesDefaults);
        this.executionHook = initExecutionHook(executionHook);

    }



    protected abstract Observable<R> getExecutionObservable();

    public Observable<R> toObservable() {
        final AbstractCommand<R> _cmd = this;

        //标记命令执行完毕
        final Action0 terminateCommandCleanup = new Action0() {

            @Override
            public void call() {
                if (_cmd.commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.TERMINAL)) {
                    metrics.markCommandDone();
                } else if (_cmd.commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.TERMINAL)) {
                    metrics.markCommandDone();
                }
            }
        };

        //触发执行的hook
        final Action0 unsubscribeCommandCleanup = new Action0() {
            @Override
            public void call() {
                try {
                    executionHook.onUnsubscribe(_cmd);
                } catch (Throwable hookEx) {
                    logger.warn("Error calling CommandExecutionHook.onUnsubscribe", hookEx);
                }
            }
        };

        //管理命令执行的生命周期
        final Func0<Observable<R>> applySemantics = new Func0<Observable<R>>() {
            @Override
            public Observable<R> call() {
                if (commandState.get().equals(CommandState.UNSUBSCRIBED)) {
                    return Observable.never();
                }
                return applySemantics(_cmd);
            }
        };

        final Func1<R, R> wrapWithAllOnNextHooks = new Func1<R, R>() {
            @Override
            public R call(R r) {

                try {
                    return executionHook.onEmit(_cmd, r);
                } catch (Throwable hookEx) {
                    logger.warn("Error calling CommandExecutionHook.onEmit", hookEx);
                    return r;
                }
            }
        };

        final Action0 fireOnCompletedHook = new Action0() {
            @Override
            public void call() {
                try {
                    executionHook.onSuccess(_cmd);
                } catch (Throwable hookEx) {
                    logger.warn("Error calling CommandExecutionHook.onSuccess", hookEx);
                }
            }
        };

        return Observable.defer(new Func0<Observable<R>>() {
            @Override
            public Observable<R> call() {
                 /* this is a stateful object so can only be used once */
                if (!commandState.compareAndSet(CommandState.NOT_STARTED, CommandState.OBSERVABLE_CHAIN_CREATED)) {
                    IllegalStateException ex = new IllegalStateException("This instance can only be executed once. " +
                            "Please instantiate a new instance.");
                    throw new IsolationRuntimeException(_cmd.getClass(), getCommandKey() + " command executed multiple " +
                            "times - this is not permitted.", ex, null);
                }

                commandStartTimestamp = System.currentTimeMillis();

                Observable<R> hystrixObservable =
                        Observable.defer(applySemantics)
                                .map(wrapWithAllOnNextHooks);

                return hystrixObservable
                        .doOnTerminate(terminateCommandCleanup)     // perform cleanup once (either on normal terminal state (this line), or unsubscribe (next line))
                        .doOnUnsubscribe(unsubscribeCommandCleanup) // perform cleanup once
                        .doOnCompleted(fireOnCompletedHook);
            }
        });
    }



    private Observable<R> applySemantics(final AbstractCommand<R> _cmd) {
        // mark that we're starting execution on the ExecutionHook
        executionHook.onStart(_cmd);

        Observable<R> execution;
        if (properties.executionTimeoutEnabled()) {
            execution = executeCommandWithSpecifiedIsolation(_cmd)
                    .lift(new ObservableTimeoutOperator<R>(_cmd));
        } else {
            execution = executeCommandWithSpecifiedIsolation(_cmd);
        }

        return execution;
    }

    private Observable<R> executeCommandWithSpecifiedIsolation(final AbstractCommand<R> _cmd) {
        // mark that we are executing in a thread (even if we end up being rejected we still were a THREAD execution and not SEMAPHORE)
        return Observable.defer(new Func0<Observable<R>>() {
            @Override
            public Observable<R> call() {
                if (!commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.USER_CODE_EXECUTED)) {
                    return Observable.error(new IllegalStateException("execution attempted while in state : " + commandState.get().name()));
                }

                metrics.markCommandStart();

                if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                    // the command timed out in the wrapping thread so we will return immediately
                    // and not increment any of the counters below or other such logic
                    return Observable.error(new RuntimeException("timed out before executing run()"));
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.STARTED)) {
                    //we have not been unsubscribed, so should proceed
                    IsolationCounters.incrementGlobalConcurrentThreads();
                    threadPool.markThreadExecution();
                    /**
                     * If any of these hooks throw an exception, then it appears as if the actual execution threw an error
                     */
                    try {
                        executionHook.onThreadStart(_cmd);
                        executionHook.onExecutionStart(_cmd);
                        return getUserExecutionObservable(_cmd);
                    } catch (Throwable ex) {
                        return Observable.error(ex);
                    }
                } else {
                    //command has already been unsubscribed, so return immediately
                    return Observable.empty();
                }
            }
        }).doOnTerminate(new Action0() {
            @Override
            public void call() {
                if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.TERMINAL)) {
                    handleThreadEnd(_cmd);
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.TERMINAL)) {
                    //if it was never started and received terminal, then no need to clean up 
                }
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.UNSUBSCRIBED)) {
                    handleThreadEnd(_cmd);
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.UNSUBSCRIBED)) {
                    //if it was never started and was cancelled, then no need to clean up
                }
                //if it was terminal, then other cleanup handled it
            }
        }).subscribeOn(threadPool.getScheduler(new Func0<Boolean>() {
            @Override
            public Boolean call() {
                return properties.executionIsolationThreadInterruptOnTimeout() &&
                        _cmd.isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT;
            }
        })).onErrorResumeNext(new Func1<Throwable, Observable<? extends R>>() {
            @Override
            public Observable<? extends R> call(Throwable throwable) {
                Exception e = getExceptionFromThrowable(throwable);
                return handleFallback(e);
            }
        });
    }


    private Observable<R> getUserExecutionObservable(final AbstractCommand<R> _cmd) {
        Observable<R> userObservable;

        try {
            userObservable = getExecutionObservable();
        } catch (Throwable ex) {
            // the run() method is a user provided implementation so can throw instead of using Observable.onError
            // so we catch it here and turn it into Observable.error
            userObservable = Observable.error(ex);
        }

        return userObservable
                .lift(new ExecutionHookApplication(_cmd));
    }

    private class ExecutionHookApplication implements Observable.Operator<R, R> {
        private final Invokable<R> cmd;

        ExecutionHookApplication(Invokable<R> cmd) {
            this.cmd = cmd;
        }

        @Override
        public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
            return new Subscriber<R>(subscriber) {
                @Override
                public void onCompleted() {
                    try {
                        executionHook.onExecutionSuccess(cmd);
                    } catch (Throwable hookEx) {
                        logger.warn("Error calling CommandExecutionHook.onExecutionSuccess", hookEx);
                    }
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    Exception wrappedEx = wrapWithOnExecutionErrorHook(e);
                    subscriber.onError(wrappedEx);
                }

                @Override
                public void onNext(R r) {
                    R wrappedValue = wrapWithOnExecutionEmitHook(r);
                    subscriber.onNext(wrappedValue);
                }
            };
        }
    }

    private Exception wrapWithOnExecutionErrorHook(Throwable t) {
        Exception e = getExceptionFromThrowable(t);
        try {
            return executionHook.onExecutionError(this, e);
        } catch (Throwable hookEx) {
            logger.warn("Error calling CommandExecutionHook.onExecutionError", hookEx);
            return e;
        }
    }

    private R wrapWithOnExecutionEmitHook(R r) {
        try {
            return executionHook.onExecutionEmit(this, r);
        } catch (Throwable hookEx) {
            logger.warn("Error calling CommandExecutionHook.onExecutionEmit", hookEx);
            return r;
        }
    }

    protected Observable<? extends R> handleFallback(Exception e) {
        if (e instanceof RejectedExecutionException) {
            threadPool.markThreadRejection();
        }
        Exception wrapped = wrapWithOnErrorHook(e);
        return Observable.error(new IsolationRuntimeException(this.getClass(), getCommandKey() +
                " could not be queued for " +
                "execution and fallback disabled.",
                wrapped,
                null));

    }

    private Exception wrapWithOnErrorHook(Throwable t) {
        Exception e = getExceptionFromThrowable(t);
        try {
            return executionHook.onError(this, e);
        } catch (Throwable hookEx) {
            logger.warn("Error calling CommandExecutionHook.onError", hookEx);
            return e;
        }
    }

    protected Exception getExceptionFromThrowable(Throwable t) {
        Exception e;
        if (t instanceof Exception) {
            e = (Exception) t;
        } else {
            e = new Exception("Throwable caught while executing.", t);
        }
        return e;
    }

    protected void handleThreadEnd(AbstractCommand<R> _cmd) {
        IsolationCounters.decrementGlobalConcurrentThreads();
        threadPool.markThreadCompletion();
        try {
            executionHook.onThreadComplete(_cmd);
        } catch (Throwable hookEx) {
            logger.warn("Error calling CommandExecutionHook.onThreadComplete", hookEx);
        }
    }

    @Override
    public String getCommandKey() {
        return commandKey;
    }

    @Override
    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    @Override
    public IsolationCommandMetrics getMetrics() {
        return metrics;
    }

    @Override
    public IsolationCommandProperties getProperties() {
        return properties;
    }

    @Override
    public boolean isExecutionComplete() {
        return commandState.get() == CommandState.TERMINAL;
    }


    @Override
    public long getCommandRunStartTimeInNanos() {
        return commandStartTimestamp;
    }

    /**
     * 处理任务是否超时
     * @param <R>
     */
    private static class ObservableTimeoutOperator<R> implements Observable.Operator<R, R> {

        final AbstractCommand<R> originalCommand;

        public ObservableTimeoutOperator(final AbstractCommand<R> originalCommand) {
            this.originalCommand = originalCommand;
        }

        @Override
        public Subscriber<? super R> call(final Subscriber<? super R> children) {
            final CompositeSubscription s = new CompositeSubscription();
            // if the children unsubscribes we unsubscribe our parent as well
            children.add(s);


            IsolationTimer.TimerListener listener = new IsolationTimer.TimerListener() {

                @Override
                public void tick() {
                    // if we can go from NOT_EXECUTED to TIMED_OUT then we do the timeout codepath
                    // otherwise it means we lost a race and the run() execution completed or did not start
                    if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.TIMED_OUT)) {

                        // shut down the original request
                        s.unsubscribe();

                    }
                }

                @Override
                public int getIntervalTimeInMilliseconds() {
                    return originalCommand.properties.executionTimeoutInMilliseconds();
                }
            };

            final Reference<IsolationTimer.TimerListener> tl = IsolationTimer.getInstance().addTimerListener(listener);

            /**
             * If this subscriber receives values it means the parent succeeded/completed
             */
            Subscriber<R> parent = new Subscriber<R>() {

                @Override
                public void onCompleted() {
                    if (isNotTimedOut()) {
                        // stop timer and pass notification through
                        tl.clear();
                        children.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (isNotTimedOut()) {
                        // stop timer and pass notification through
                        tl.clear();
                        children.onError(e);
                    }
                }

                @Override
                public void onNext(R v) {
                    if (isNotTimedOut()) {
                        children.onNext(v);
                    }
                }

                private boolean isNotTimedOut() {
                    // if already marked COMPLETED (by onNext) or succeeds in setting to COMPLETED
                    return originalCommand.isCommandTimedOut.get() == TimedOutStatus.COMPLETED ||
                            originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED);
                }

            };

            s.add(parent);

            return parent;
        }

    }


    private CommandExecutionHook initExecutionHook(CommandExecutionHook executionHook) {
        if (executionHook == null) {
            executionHook = new CommandExecutionHook() {
                @Override
                public <T> void onStart(Invokable<T> commandInstance) {
                    super.onStart(commandInstance);
                }

                @Override
                public <T> T onEmit(Invokable<T> commandInstance, T value) {
                    return super.onEmit(commandInstance, value);
                }

                @Override
                public <T> Exception onError(Invokable<T> commandInstance, Exception e) {
                    return super.onError(commandInstance, e);
                }

                @Override
                public <T> void onSuccess(Invokable<T> commandInstance) {
                    super.onSuccess(commandInstance);
                }

                @Override
                public <T> void onThreadStart(Invokable<T> commandInstance) {
                    super.onThreadStart(commandInstance);
                }

                @Override
                public <T> void onThreadComplete(Invokable<T> commandInstance) {
                    super.onThreadComplete(commandInstance);
                }

                @Override
                public <T> void onExecutionStart(Invokable<T> commandInstance) {
                    super.onExecutionStart(commandInstance);
                }

                @Override
                public <T> T onExecutionEmit(Invokable<T> commandInstance, T value) {
                    return super.onExecutionEmit(commandInstance, value);
                }

                @Override
                public <T> Exception onExecutionError(Invokable<T> commandInstance, Exception e) {
                    return super.onExecutionError(commandInstance, e);
                }

                @Override
                public <T> void onExecutionSuccess(Invokable<T> commandInstance) {
                    super.onExecutionSuccess(commandInstance);
                }

                @Override
                public <T> void onUnsubscribe(Invokable<T> commandInstance) {
                    super.onUnsubscribe(commandInstance);
                }
            };
        }
        return executionHook;
    }
}
