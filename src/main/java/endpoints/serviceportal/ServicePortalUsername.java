package endpoints.serviceportal;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;

@Value
public class ServicePortalUsername implements Serializable {
    public final @Nonnull String username;
}
