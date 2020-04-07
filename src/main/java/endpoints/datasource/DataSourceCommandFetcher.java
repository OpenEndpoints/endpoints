package endpoints.datasource;

import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

public abstract class DataSourceCommandFetcher implements Runnable {
    
    protected Element[] result;
    
    /** @return does not have parameters expanded */
    protected abstract @Nonnull Element[] populateOrThrow() throws TransformationFailedException;

    public synchronized @Nonnull Element[] get() {
        return result;
    }

    // Called from ThreadPool once this is scheduled
    @SneakyThrows(TransformationFailedException.class)
    @Override
    public void run() {
        var r = populateOrThrow();
        synchronized (this) { result = r; }
    }
}
