package come.ezlippi.isolation.exception;

import come.ezlippi.isolation.Invokable;
import come.ezlippi.isolation.IsolationCommand;

public class IsolationRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 5219160332476046229L;

    private final Class<? extends Invokable> commandClass;
    private final Throwable fallbackException;


    public IsolationRuntimeException(Class<? extends Invokable> commandClass, String message, Exception cause, Throwable fallbackException) {
        super(message, cause);
        this.commandClass = commandClass;
        this.fallbackException = fallbackException;
    }

    public IsolationRuntimeException(Class<? extends Invokable> commandClass, String message, Throwable cause, Throwable fallbackException) {
        super(message, cause);
        this.commandClass = commandClass;
        this.fallbackException = fallbackException;
    }

    /**
     * The implementing class of the {@link IsolationCommand}.
     *
     * @return {@code Class<? extends HystrixCommand> }
     */
    public Class<? extends Invokable> getImplementingClass() {
        return commandClass;
    }

    /**
     * The {@link Throwable} that was thrown when trying to retrieve a fallback.
     *
     * @return {@link Throwable}
     */
    public Throwable getFallbackException() {
        return fallbackException;
    }

}

