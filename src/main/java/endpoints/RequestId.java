package endpoints;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.UUID;

@Value
public class RequestId implements Serializable {
    
    public final @Nonnull UUID id;

    public static @Nonnull RequestId newRandom() {
        return new RequestId(UUID.randomUUID());
    }
}
