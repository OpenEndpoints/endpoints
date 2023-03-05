package endpoints.jooq;

import endpoints.config.NodeName;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

public class NodeNameConverter implements Converter<String, NodeName> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<NodeName> toType() { return NodeName.class; }

    @Override
    public @CheckForNull NodeName from(@CheckForNull String x) {
        if (x == null) return null;
        return new NodeName(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull NodeName x) {
        if (x == null) return null;
        return x.name;
    }
}
