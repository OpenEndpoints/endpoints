package endpoints.datasource;

import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

public abstract class DataSourceCommandResult implements Runnable {
    
    protected Element[] result;
    
    /** @return does not have parameters expanded */
    protected abstract @Nonnull Element[] populateOrThrow() throws TransformationFailedException;

    public synchronized @Nonnull Element[] get() {
        return result;
    }

    @SneakyThrows(TransformationFailedException.class)
    @Override
    public void run() {
        var r = populateOrThrow();
        synchronized (this) { result = r; }
    }
}
