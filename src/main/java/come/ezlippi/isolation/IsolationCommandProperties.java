package come.ezlippi.isolation;

/**
 * 隔离参数
 */
public class IsolationCommandProperties {

    private static final Integer default_executionTimeoutInMilliseconds = 5000;

    private static final Boolean default_executionTimeoutEnabled = false;

    private static final Boolean default_InterruptExecuteOnTimeout = true;

    private static final Boolean default_InterruptExecuteOnFutureCancel = false;

    private static final Boolean default_requestLogEnabled = false;

    private final String key;

    /**
     * Timeout value in milliseconds for a command
     */
    private final Integer executionTimeoutInMilliseconds;

    /**
     * Whether timeout should be triggered
     */
    private final Boolean executionTimeoutEnabled;


    /**
     * Whether an underlying Future/Thread (when runInSeparateThread == true) should be interrupted after a timeout
     */
    private final Boolean interruptExecuteOnTimeout;

    /**
     *  Whether canceling an underlying Future/Thread (when runInSeparateThread == true) should interrupt the execution thread
     */
    private final Boolean interruptExecuteOnFutureCancel;


    public IsolationCommandProperties(String key) {
        this(key, new Builder());
    }

    public IsolationCommandProperties(String key, IsolationCommandProperties.Builder builder) {
        this.key = key;
        this.executionTimeoutInMilliseconds = builder.executionTimeoutInMilliseconds == null ?
                default_executionTimeoutInMilliseconds : builder.executionTimeoutInMilliseconds;

        this.executionTimeoutEnabled =  builder.executionTimeoutEnabled == null ?
                default_executionTimeoutEnabled : builder.executionTimeoutEnabled;

        this.interruptExecuteOnTimeout =  builder.interruptExecutionOnTimeout == null ?
                default_InterruptExecuteOnTimeout : builder.interruptExecutionOnTimeout;

        this.interruptExecuteOnFutureCancel =  builder.interruptExecutionOnFutureCancel == null ?
                default_InterruptExecuteOnFutureCancel : builder.interruptExecutionOnFutureCancel;

    }

    public Boolean executionIsolationThreadInterruptOnTimeout() {
        return interruptExecuteOnTimeout;
    }


    public Boolean executionIsolationThreadInterruptOnFutureCancel() {
        return interruptExecuteOnFutureCancel;
    }

    public Integer executionTimeoutInMilliseconds() {
        return executionTimeoutInMilliseconds;
    }

    public Boolean executionTimeoutEnabled() {
        return executionTimeoutEnabled;
    }

    public static class Builder {

        private Boolean interruptExecutionOnTimeout = null;
        private Boolean interruptExecutionOnFutureCancel = null;
        private Integer executionTimeoutInMilliseconds = null;
        private Boolean executionTimeoutEnabled = null;

        public Builder() {
        }

        public Boolean getInterruptExecutionOnTimeout() {
            return interruptExecutionOnTimeout;
        }

        public Boolean getInterruptExecutionOnFutureCancel() {
            return interruptExecutionOnFutureCancel;
        }

        public Integer getExecutionTimeoutInMilliseconds() {
            return executionTimeoutInMilliseconds;
        }

        public Boolean getExecutionTimeoutEnabled() {
            return executionTimeoutEnabled;
        }


        public Builder withExecutionIsolationThreadInterruptOnTimeout(boolean value) {
            this.interruptExecutionOnTimeout = value;
            return this;
        }

        public Builder withExecutionIsolationThreadInterruptOnFutureCancel(boolean value) {
            this.interruptExecutionOnFutureCancel = value;
            return this;
        }

        public Builder withExecutionTimeoutInMilliseconds(int value) {
            this.executionTimeoutInMilliseconds = value;
            return this;
        }

        public Builder withExecutionTimeoutEnabled(boolean value) {
            this.executionTimeoutEnabled = value;
            return this;
        }

    }

}
