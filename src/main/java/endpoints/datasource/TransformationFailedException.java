package endpoints.datasource;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class TransformationFailedException extends Exception {
    public TransformationFailedException(@Nonnull String msg) { super(msg); }
    public TransformationFailedException(@Nonnull Throwable cause) { this(null, cause); }
    public TransformationFailedException(@CheckForNull String prefix, @Nonnull Throwable cause) {
        super((prefix == null ? "" : (prefix + ": ")) + ((cause.getMessage() == null) ? "Internal error" : cause.getMessage()), cause);
    }
}
