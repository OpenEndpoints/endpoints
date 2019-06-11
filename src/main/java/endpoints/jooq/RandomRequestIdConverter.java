package endpoints.jooq;

import endpoints.RandomRequestId;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class RandomRequestIdConverter implements Converter<Long, RandomRequestId> {

    @Override public Class<Long> fromType() { return Long.class; }
    @Override public Class<RandomRequestId> toType() { return RandomRequestId.class; }

    @Override
    public @CheckForNull RandomRequestId from(@CheckForNull Long x) {
        if (x == null) return null;
        return new RandomRequestId(x);
    }

    @Override
    public @CheckForNull Long to(@CheckForNull RandomRequestId x) {
        if (x == null) return null;
        return x.getId();
    }
}
