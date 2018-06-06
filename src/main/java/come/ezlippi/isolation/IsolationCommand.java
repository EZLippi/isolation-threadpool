package come.ezlippi.isolation;

import come.ezlippi.isolation.exception.IsolationRuntimeException;
import come.ezlippi.isolation.hook.CommandExecutionHook;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func0;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class IsolationCommand<R> extends AbstractCommand<R> {

    protected IsolationCommand(Builder builder) {
        this(builder.commandKey, builder.threadPoolKey, builder.commandPropertiesBuilder,
                builder.threadPoolPropertiesBuilder, null);
    }

    protected IsolationCommand( String key, String threadPoolKey,
                   IsolationCommandProperties.Builder commandPropertiesDefaults, IsolationThreadPoolProperties.Builder threadPoolPropertiesDefaults,
                   CommandExecutionHook executionHook) {
        super(key, threadPoolKey,  commandPropertiesDefaults, threadPoolPropertiesDefaults,  executionHook);
    }
    
    public static class Builder {

        protected String commandKey;
        protected String threadPoolKey;
        protected IsolationCommandProperties.Builder commandPropertiesBuilder;
        protected IsolationThreadPoolProperties.Builder threadPoolPropertiesBuilder;
        protected CommandExecutionHook commandExecutionHook;


        public Builder withCommandKey(String commandKey) {
            this.commandKey = commandKey;
            return this;
        }

        public Builder withThreadPoolKey(String threadPoolKey) {
            this.threadPoolKey = threadPoolKey;
            return this;
        }

        public Builder withCommandPropertiesBuilder(IsolationCommandProperties.Builder commandPropertiesBuilder) {
            this.commandPropertiesBuilder = commandPropertiesBuilder;
            return this;
        }

        public Builder withThreadPoolPropertiesBuilder(IsolationThreadPoolProperties.Builder threadPoolPropertiesBuilder) {
            this.threadPoolPropertiesBuilder = threadPoolPropertiesBuilder;
            return this;
        }

        public Builder withCommandExecutionHook(CommandExecutionHook executionHook) {
            this.commandExecutionHook = executionHook;
            return this;
        }

    }

    /**
     * 执行这个命令的线程,用于中断执行的线程
     */
    private final AtomicReference<Thread> executionThread = new AtomicReference<Thread>();

    private final AtomicBoolean interruptOnFutureCancel = new AtomicBoolean(false);

    /**
     * 具体的命令,需要在run方法里实现具体的业务逻辑,比如调用第三方服务的接口
     * @return  响应
     * @throws Exception    可能抛出异常
     */
    protected abstract R run() throws Exception;

    @Override
    final protected Observable<R> getExecutionObservable() {
        //创建Observable对象
        return Observable.defer(new Func0<Observable<R>>() {
            @Override
            public Observable<R> call() {
                try {
                    //执行run()方法里的业务逻辑
                    return Observable.just(run());
                } catch (Throwable t) {
                    return Observable.error(t);
                }
            }
        }).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                // 保存这个任务关联的线程,如果有需要我们可以中断它
                executionThread.set(Thread.currentThread());
            }
        });
    }

    /**
     * 同步调用获取执行结果
     * @return  返回结果
     */
    public R execute() {
        try {
            return queue().get();
        } catch (ExecutionException e) {
            throw new IsolationRuntimeException(this.getClass(), "executionException", e, e);
        } catch (InterruptedException e) {
            throw new IsolationRuntimeException(this.getClass(), "InterruptException", e, e);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 异步执行一个命令,会把命令提交到线程池中,返回一个future给调用者,调用者可以通过future来获取返回结果
     */
    public Future<R> queue() {

         //Observable.toBlocking().toFuture()返回的Future在调用Future.cancel(true)时没有实现中断执行线程的机制,因此这里做一个封装
        final Future<R> originFuture = toObservable().toBlocking().toFuture();

        final Future<R> wrapedFuture = new Future<R>() {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (originFuture.isCancelled()) {
                    return false;
                }

                if (IsolationCommand.this.getProperties().executionIsolationThreadInterruptOnFutureCancel()) {
                    /*
                     * The only valid transition here is false -> true. If there are two futures, say f1 and f2, created by this command
                     * (which is super-weird, but has never been prohibited), and calls to f1.cancel(true) and to f2.cancel(false) are
                     * issued by different threads, it's unclear about what value would be used by the time mayInterruptOnCancel is checked.
                     * The most consistent way to deal with this scenario is to say that if *any* cancellation is invoked with interruption,
                     * than that interruption request cannot be taken back.
                     */
                    interruptOnFutureCancel.compareAndSet(false, mayInterruptIfRunning);
                }

                final boolean result = originFuture.cancel(interruptOnFutureCancel.get());

                if (!isExecutionComplete() && interruptOnFutureCancel.get()) {
                    final Thread t = executionThread.get();
                    if (t != null && !t.equals(Thread.currentThread())) {
                        t.interrupt();
                    }
                }

                return result;
            }

            @Override
            public boolean isCancelled() {
                return originFuture.isCancelled();
            }

            @Override
            public boolean isDone() {
                return originFuture.isDone();
            }

            @Override
            public R get() throws InterruptedException, ExecutionException {
                return originFuture.get();
            }

            @Override
            public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return originFuture.get(timeout, unit);
            }

        };

        return wrapedFuture;
    }

    @Override
    public Thread getExecutionThread() {
        return executionThread.get();
    }
}
