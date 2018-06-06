package come.ezlippi.isolation;

/**
 * 线程池参数配置
 */
public class IsolationThreadPoolProperties {
    static int default_coreSize = 10;            // core size of thread pool
    static int default_maximumSize = 10;         // maximum size of thread pool
    static int default_keepAliveTimeMinutes = 5; // minutes to keep a thread alive
    static int default_maxQueueSize = -1;       //working queue size,default in infinity


    private final Integer coreSize;

    private final Integer maxSize;

    private final Integer keepAliveTimeInMinutes;

    private final Integer workingQueueSize;

    public IsolationThreadPoolProperties() {
        this(new Builder());
    }

    public IsolationThreadPoolProperties(IsolationThreadPoolProperties.Builder builder) {
        coreSize = builder.coreSize == null ? default_coreSize : builder.coreSize;
        maxSize = builder.maxSize == null ? default_maximumSize : builder.maxSize;
        keepAliveTimeInMinutes = builder.keepAliveTimeInMinutes == null ? default_keepAliveTimeMinutes : builder.keepAliveTimeInMinutes;
        workingQueueSize = builder.workingQueueSize == null ? default_maxQueueSize : builder.workingQueueSize;
    }

    public Integer getCoreSize() {
        return coreSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public Integer getKeepAliveTimeInMinutes() {
        return keepAliveTimeInMinutes;
    }

    public Integer getWorkingQueueSize() {
        return workingQueueSize;
    }

    public static class Builder {
        private  Integer coreSize;

        private  Integer maxSize;

        private  Integer keepAliveTimeInMinutes;

        private  Integer workingQueueSize;

        public Builder() {}

        public Integer getCoreSize() {
            return coreSize;
        }

        public Integer getMaximumSize() {
            return maxSize;
        }

        public Integer getKeepAliveTimeMinutes() {
            return keepAliveTimeInMinutes;
        }

        public Integer getMaxQueueSize() {
            return workingQueueSize;
        }

        public Builder withCoreSize(int value) {
            this.coreSize = value;
            return this;
        }

        public Builder withMaximumSize(int value) {
            this.maxSize = value;
            return this;
        }

        public Builder withKeepAliveTimeMinutes(int value) {
            this.keepAliveTimeInMinutes = value;
            return this;
        }

        public Builder withMaxQueueSize(int value) {
            this.workingQueueSize = value;
            return this;
        }

        public IsolationThreadPoolProperties build() {
            return new IsolationThreadPoolProperties(this);
        }
    }
}
