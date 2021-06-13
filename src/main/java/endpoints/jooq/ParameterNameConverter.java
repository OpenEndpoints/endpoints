package endpoints.jooq;

import endpoints.config.ParameterName;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class ParameterNameConverter implements Converter<String, ParameterName> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<ParameterName> toType() { return ParameterName.class; }

    @Override
    public @CheckForNull ParameterName from(@CheckForNull String x) {
        if (x == null) return null;
        return new ParameterName(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull ParameterName x) {
        if (x == null) return null;
        return x.name;
    }
}
