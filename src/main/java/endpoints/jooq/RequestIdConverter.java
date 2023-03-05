package endpoints.jooq;

import endpoints.RequestId;
import org.jooq.Converter;

import javax.annotation.CheckForNull;
import java.util.UUID;

public class RequestIdConverter implements Converter<UUID, RequestId> {

    @Override public Class<UUID> fromType() { return UUID.class; }
    @Override public Class<RequestId> toType() { return RequestId.class; }

    @Override
    public @CheckForNull RequestId from(@CheckForNull UUID x) {
        if (x == null) return null;
        return new RequestId(x);
    }

    @Override
    public @CheckForNull UUID to(@CheckForNull RequestId x) {
        if (x == null) return null;
        return x.id();
    }
}
