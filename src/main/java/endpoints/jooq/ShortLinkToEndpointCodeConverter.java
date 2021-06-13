package endpoints.jooq;

import endpoints.ShortLinkToEndpointCode;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class ShortLinkToEndpointCodeConverter implements Converter<String, ShortLinkToEndpointCode> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<ShortLinkToEndpointCode> toType() { return ShortLinkToEndpointCode.class; }

    @Override
    public @CheckForNull ShortLinkToEndpointCode from(@CheckForNull String x) {
        if (x == null) return null;
        return new ShortLinkToEndpointCode(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull ShortLinkToEndpointCode x) {
        if (x == null) return null;
        return x.getCode();
    }
}
