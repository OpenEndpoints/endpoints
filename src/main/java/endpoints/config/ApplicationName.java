package endpoints.config;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.UUID;

public record ApplicationName(
    @Nonnull String name
) implements Serializable {
    public static @Nonnull ApplicationName newRandomForTesting() {
        return new ApplicationName(UUID.randomUUID().toString());
    }
}
