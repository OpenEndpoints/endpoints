package endpoints.jooq;

import endpoints.config.ApplicationName;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

public class ApplicationNameConverter implements Converter<String, ApplicationName> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<ApplicationName> toType() { return ApplicationName.class; }

    @Override
    public @CheckForNull ApplicationName from(@CheckForNull String x) {
        if (x == null) return null;
        return new ApplicationName(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull ApplicationName a) {
        if (a == null) return null;
        return a.name();
    }
}
