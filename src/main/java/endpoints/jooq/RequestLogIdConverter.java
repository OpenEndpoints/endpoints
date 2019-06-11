package endpoints.jooq;

import endpoints.RequestLogId;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class RequestLogIdConverter implements Converter<Long, RequestLogId> {

    @Override public Class<Long> fromType() { return Long.class; }
    @Override public Class<RequestLogId> toType() { return RequestLogId.class; }

    @Override
    public @CheckForNull RequestLogId from(@CheckForNull Long x) {
        if (x == null) return null;
        return new RequestLogId(x);
    }

    @Override
    public @CheckForNull Long to(@CheckForNull RequestLogId x) {
        if (x == null) return null;
        return x.getId();
    }
}
