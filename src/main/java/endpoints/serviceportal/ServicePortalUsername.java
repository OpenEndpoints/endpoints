package endpoints.serviceportal;

import javax.annotation.Nonnull;
import java.io.Serializable;

public record ServicePortalUsername(
    @Nonnull String username
) implements Serializable {
}
