package endpoints.datasource;

import com.databasesandlife.util.Future;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

public abstract class DataSourceCommandResult extends Future<Element[]> {

    /** @return does not have parameters expanded */
    protected abstract @Nonnull Element[] populateOrThrow() throws TransformationFailedException;

    @Override
    @SneakyThrows(TransformationFailedException.class)
    protected final @Nonnull Element[] populate() {
        return populateOrThrow();
    }

    /** @return does not have parameters expanded */
    public @Nonnull Element[] getOrThrow() throws TransformationFailedException {
        try { return get(); }
        catch (FuturePopulationException e) {
            if (e.getCause() instanceof TransformationFailedException)
                throw (TransformationFailedException) e.getCause();
            else throw e;
        }
    }
}
