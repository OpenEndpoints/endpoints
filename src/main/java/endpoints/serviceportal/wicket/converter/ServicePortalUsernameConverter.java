package endpoints.serviceportal.wicket.converter;

import endpoints.serviceportal.ServicePortalUsername;
import org.apache.wicket.util.convert.ConversionException;
import org.apache.wicket.util.convert.IConverter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Locale;

public class ServicePortalUsernameConverter implements IConverter<ServicePortalUsername> {

    @Override public @CheckForNull ServicePortalUsername convertToObject(@CheckForNull String value, @Nonnull Locale locale)
    throws ConversionException {
        if (value == null) return null;
        return new ServicePortalUsername(value);
    }

    @Override public @CheckForNull String convertToString(@CheckForNull ServicePortalUsername value, @Nonnull Locale locale) {
        if (value == null) return null;
        return value.username();
    }
}
