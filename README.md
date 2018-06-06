# isolation-threadpool
从Hystrix核心代码中提取出来的线程池隔离的代码,可以非常方便的在Web应用中实现线程池隔离

# 使用场景

我们的应用在使用Jetty服务时，多个HTTP服务会共享同一个线程池，当其中一个服务依赖的其他服务响应慢时造成服务响应时间增加，大多数线程阻塞等待数据响应返回，新的请求无法建立SSL连接,导致整个Jetty线程池都被
该服务占用，最终拖垮了整个Jetty,因此我们有必要能把不同HTTP服务隔离到不同的线程池中，即使其中某个HTTP服务的线程池满了也不会影响其他的服务

# 如何使用

1. 通过Builder创建命令参数,可以配置任务执行的时间,超时后执行线程会被中断

``` java
IsolationCommandProperties.Builder builder1 = new IsolationCommandProperties.Builder()
                .withExecutionTimeoutEnabled(true)
                .withExecutionTimeoutInMilliseconds(1000)
                .withExecutionIsolationThreadInterruptOnTimeout(true);
```

2. 创建线程池的核心参数

``` java
IsolationThreadPoolProperties.Builder threadPoolBuilder1 = new IsolationThreadPoolProperties.Builder()
        .withCoreSize(1)
        .withMaximumSize(1)
        .withKeepAliveTimeMinutes(2)
        .withMaxQueueSize(10);
```
3. 创建一个IsolationCommand，一个IsolationCommand对应一个任务的执行,比如第三方服务的调用，需要指定commandName和threadPoolKey,不同的threadPoolKey会创建不同线程池

``` java
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
  //将任务提交给线程池处理,queue()方法返回一个Future,调用者可以通过轮询或者阻塞获取返回值
  command1.queue();
  //调用线程同步获取返回值
  command2.execute();
  ```
