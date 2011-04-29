package endpoints.config;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;

@SuppressWarnings("serial")
@Value
public class ParameterName implements Serializable, Comparable<ParameterName> {
    
    public final @Nonnull String name;

    @Override public int compareTo(@Nonnull ParameterName x) { return name.compareTo(x.name); }
}
