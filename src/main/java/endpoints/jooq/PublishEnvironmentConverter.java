package endpoints.jooq;

import endpoints.PublishEnvironment;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

public class PublishEnvironmentConverter implements Converter<String, PublishEnvironment> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<PublishEnvironment> toType() { return PublishEnvironment.class; }

    @Override
    public @CheckForNull PublishEnvironment from(@CheckForNull String x) {
        if (x == null) return null;
        return PublishEnvironment.valueOf(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull PublishEnvironment x) {
        if (x == null) return null;
        return x.name();
    }
}
