package endpoints.config;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;

@SuppressWarnings("serial")
@Value
public class IntermediateValueName implements Serializable, Comparable<IntermediateValueName> {
    
    public final @Nonnull String name;

    @Override public int compareTo(@Nonnull IntermediateValueName x) { return name.compareTo(x.name); }
}
