package endpoints;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;

/**
 * A value that is computed on demand.
 *    <p>
 * This is necessary for secrets, which should only throw an error if they do not exist and are actually accessed.
 * (i.e. secrets which are referenced but which do not exist on AWS, and which are never actually referenced, should not error.)
 */
public abstract class LazyCachingValue {

    // This is a RuntimeException because there are just too many places that deal with variables to add "throws" everywhere
    public static class LazyParameterComputationException extends RuntimeException {
        public LazyParameterComputationException(String prefix, Throwable t) { super(prefixExceptionMessage(prefix, t), t); }
    }
    
    protected @CheckForNull String value = null;
    
    protected abstract @Nonnull String computeParameter() throws LazyParameterComputationException;
  
    public synchronized @Nonnull String get() throws LazyParameterComputationException {
        if (value == null) value = computeParameter();
        return value;
    }
    
    public static @Nonnull LazyCachingValue newFixed(@Nonnull String fixedValue) {
        return new LazyCachingValue() {
            @Override protected @Nonnull String computeParameter() {
                return fixedValue;
            }
        };
    }
}
