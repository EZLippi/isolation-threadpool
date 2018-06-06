package com.ezlippi.isolation;

import come.ezlippi.isolation.Invokable;
import come.ezlippi.isolation.IsolationCommand;
import come.ezlippi.isolation.IsolationCommandProperties;
import come.ezlippi.isolation.IsolationThreadPoolProperties;
import come.ezlippi.isolation.hook.CommandExecutionHook;
import org.junit.Test;

public class CommandTest {

    @Test
    public void testIsolationThreadPool() {
        IsolationCommandProperties.Builder builder1 = new IsolationCommandProperties.Builder()
                .withExecutionTimeoutEnabled(true)
                .withExecutionTimeoutInMilliseconds(1000)
                .withExecutionIsolationThreadInterruptOnTimeout(true);

        IsolationThreadPoolProperties.Builder threadPoolBuilder1 = new IsolationThreadPoolProperties.Builder()
                .withCoreSize(1)
                .withMaximumSize(1)
                .withKeepAliveTimeMinutes(2)
                .withMaxQueueSize(10);

        IsolationCommand<String> command1 = new IsolationCommand<String>("command1", "pool1",
                builder1, threadPoolBuilder1, new DefaultExecutionHook()) {
            @Override
            protected String run() throws Exception {
                return "hello";
            }
        };
        IsolationCommand<String> command2 = new IsolationCommand<String>("command2", "pool2",
                builder1, threadPoolBuilder1, new DefaultExecutionHook()) {
            @Override
            protected String run() throws Exception {
                return "isolation";
            }
        };
        command1.execute();
        command2.execute();
    }

    static class DefaultExecutionHook extends CommandExecutionHook {
        @Override
        public <T> void onStart(Invokable<T> commandInstance) {
        }

        @Override
        public <T> T onEmit(Invokable<T> commandInstance, T value) {
            return value;
        }

        @Override
        public <T> Exception onError(Invokable<T> commandInstance, Exception e) {
            return e;
        }

        @Override
        public <T> void onSuccess(Invokable<T> commandInstance) {
        }

        @Override
        public <T> void onThreadStart(Invokable<T> commandInstance) {
        }

        @Override
        public <T> void onThreadComplete(Invokable<T> commandInstance) {
        }

        @Override
        public <T> void onExecutionStart(Invokable<T> command) {
        }

        @Override
        public <T> T onExecutionEmit(Invokable<T> command, T value) {
            System.out.println(command.getCommandKey() + " execution on " + command.getExecutionThread().getName());
            return value;
        }

        @Override
        public <T> Exception onExecutionError(Invokable<T> commandInstance, Exception e) {
            return e;
        }

        @Override
        public <T> void onExecutionSuccess(Invokable<T> commandInstance) {
        }

        @Override
        public <T> void onUnsubscribe(Invokable<T> commandInstance) {
        }
    }
}
