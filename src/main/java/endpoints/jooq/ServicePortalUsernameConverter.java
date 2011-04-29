package endpoints.jooq;

import endpoints.serviceportal.ServicePortalUsername;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class ServicePortalUsernameConverter implements Converter<String, ServicePortalUsername> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<ServicePortalUsername> toType() { return ServicePortalUsername.class; }

    @Override
    public @CheckForNull ServicePortalUsername from(@CheckForNull String x) {
        if (x == null) return null;
        return new ServicePortalUsername(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull ServicePortalUsername x) {
        if (x == null) return null;
        return x.username;
    }
}
