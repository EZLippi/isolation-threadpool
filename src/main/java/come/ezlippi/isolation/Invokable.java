package come.ezlippi.isolation;

/**
 * 所有可以被线程池执行的任务
 */
public interface Invokable<R> {

    /**
     * 命令的key
     * @return 命令的key
     */
    String getCommandKey();

    /**
     * 线程池key,标识用哪一组线程池来执行这个任务
     * @return  线程池key
     */
    String getThreadPoolKey();

    IsolationCommandMetrics getMetrics();

    IsolationCommandProperties getProperties();

    boolean isExecutionComplete();

    long getCommandRunStartTimeInNanos();

    Thread getExecutionThread();

}
