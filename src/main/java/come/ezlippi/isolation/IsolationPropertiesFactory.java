package come.ezlippi.isolation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性工厂类,保存所有命令参数和线程池参数
 */
public class IsolationPropertiesFactory {

    private static final Map<String, IsolationThreadPoolProperties> THREAD_POOL_PROPERTIES_MAP =
            new ConcurrentHashMap<String, IsolationThreadPoolProperties>();

    private static final Map<String, IsolationCommandProperties> commandProperties =
            new ConcurrentHashMap<String, IsolationCommandProperties>();

    public static void clear() {
        THREAD_POOL_PROPERTIES_MAP.clear();
        commandProperties.clear();
    }

    public static IsolationThreadPoolProperties getThreadPoolProperties(String threadPoolKey, IsolationThreadPoolProperties.Builder builder) {
        IsolationThreadPoolProperties poolProperties = THREAD_POOL_PROPERTIES_MAP.get(threadPoolKey);
        if (poolProperties == null) {
            poolProperties = new IsolationThreadPoolProperties(builder);
            IsolationThreadPoolProperties exists = THREAD_POOL_PROPERTIES_MAP.putIfAbsent(threadPoolKey, poolProperties);
            if (exists == null) {
                return poolProperties;
            } else {
                return exists;
            }
        }
        return poolProperties;
    }

    public static IsolationCommandProperties getCommandProperties(String key, IsolationCommandProperties.Builder builder) {
            IsolationCommandProperties properties = commandProperties.get(key);
            if (properties != null) {
                return properties;
            } else {
                if (builder == null) {
                    builder = new IsolationCommandProperties.Builder();
                }
                // create new instance
                properties = new IsolationCommandProperties(key, builder);
                // cache and return
                IsolationCommandProperties existing = commandProperties.putIfAbsent(key, properties);
                if (existing == null) {
                    return properties;
                } else {
                    return existing;
                }
            }
    }



}
