package come.ezlippi.isolation;

import rx.Scheduler;
import rx.functions.Func0;

import java.util.concurrent.ExecutorService;

/**
 * 隔离线程池,每个线程用来执行{@link IsolationCommand#run()}方法,不同的服务应设置不同的key来区分不同的线程池
 */
public interface IsolationThreadPool {

    /**
     * 返回ThreadPoolExecutor的实例
     *
     * @return ThreadPoolExecutor
     */
    ExecutorService getExecutor();

    Scheduler getScheduler();

    Scheduler getScheduler(Func0<Boolean> shouldInterruptThread);


    /**
     * 标记一个线程开始执行一个任务
     */
    void markThreadExecution();

    /**
     * 标记线程执行完一个任务
     */
    void markThreadCompletion();

    /**
     * 标记一个任务被线程池拒绝执行
     */
    void markThreadRejection();


    /**
     * 线程池工作队列是否已满
     *
     * @return 队列是否已满
     */
    boolean isQueueSpaceAvailable();

}
