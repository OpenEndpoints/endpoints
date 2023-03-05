package endpoints;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.UUID;

public record RequestId(
    @Nonnull UUID id
) implements Serializable {
    public static @Nonnull RequestId newRandom() {
        return new RequestId(UUID.randomUUID());
    }
}
