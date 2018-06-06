package come.ezlippi.isolation.hook;

import come.ezlippi.isolation.Invokable;
import come.ezlippi.isolation.IsolationCommand;

/**
 * 管理命令执行的生命周期
 */
public abstract class CommandExecutionHook {
    /**
     * Invoked before {@link Invokable} begins executing.
     *
     * @param commandInstance The executing Invokable instance.
     *
     */
    public <T> void onStart(Invokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when {@link Invokable} emits a value.
     *
     * @param commandInstance The executing Invokable instance.
     * @param value value emitted
     *
     */
    public <T> T onEmit(Invokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when {@link Invokable} fails with an Exception.
     *
     * @param commandInstance The executing Invokable instance.
     * @param e exception object
     *
     */
    public <T> Exception onError(Invokable<T> commandInstance, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when {@link Invokable} finishes a successful execution.
     *
     * @param commandInstance The executing Invokable instance.
     *
     */
    public <T> void onSuccess(Invokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at start of thread execution when {@link IsolationCommand} is executed .
     *
     * @param commandInstance The executing IsolationCommand instance.
     *
     */
    public <T> void onThreadStart(Invokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at completion of thread execution when {@link IsolationCommand} is executed .
     * This will get invoked whenever the Hystrix thread is done executing, regardless of whether the thread finished
     * naturally, or was unsubscribed externally
     *
     * @param commandInstance The executing IsolationCommand instance.
     *
     */
    public <T> void onThreadComplete(Invokable<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link Invokable} starts.
     *
     * @param commandInstance The executing Invokable instance.
     *
     */
    public <T> void onExecutionStart(Invokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link Invokable} emits a value.
     *
     * @param commandInstance The executing Invokable instance.
     * @param value value emitted
     *
     */
    public <T> T onExecutionEmit(Invokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link Invokable} fails with an Exception.
     *
     * @param commandInstance The executing Invokable instance.
     * @param e exception object
     *
     */
    public <T> Exception onExecutionError(Invokable<T> commandInstance, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link Invokable} completes successfully.
     *
     * @param commandInstance The executing Invokable instance.
     *
     */
    public <T> void onExecutionSuccess(Invokable<T> commandInstance) {
        //do nothing by default
    }


    /**
     * Invoked with the command is unsubscribed before a terminal state
     *
     * @param commandInstance The executing Invokable instance.
     *
     */
    public <T> void onUnsubscribe(Invokable<T> commandInstance) {
        //do nothing by default
    }


}

