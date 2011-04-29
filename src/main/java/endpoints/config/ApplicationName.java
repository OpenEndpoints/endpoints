package endpoints.config;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.UUID;

@SuppressWarnings("serial")
@Value
public class ApplicationName implements Serializable, Comparable<ApplicationName> {
    
    public final @Nonnull String name;

    @Override public int compareTo(@Nonnull ApplicationName x) { return name.compareTo(x.name); }
    
    public static @Nonnull ApplicationName newRandomForTesting() {
        return new ApplicationName(UUID.randomUUID().toString());
    }
}
