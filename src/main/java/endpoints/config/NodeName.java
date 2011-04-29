package endpoints.config;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;

@SuppressWarnings("serial")
@Value
public class NodeName implements Serializable, Comparable<NodeName> {
    
    public final @Nonnull String name;

    @Override public int compareTo(@Nonnull NodeName x) { return name.compareTo(x.name); }
}
