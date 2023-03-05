package endpoints.serviceportal.wicket;

import endpoints.config.ApplicationName;
import endpoints.serviceportal.ServicePortalUsername;
import lombok.AllArgsConstructor;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Locale;

public class ServicePortalSession extends WebSession {

    @AllArgsConstructor
    public static final class LoggedInUserData implements Serializable {
        public final @Nonnull ServicePortalUsername username;
        public final boolean isAdmin;
        /** if the user has access to more than one application, they can see the "change application" link */
        public boolean moreThanOneApplication;
    }

    public record LoggedInApplicationData(
        @Nonnull ApplicationName application,
        @Nonnull String applicationDisplayName
    ) implements Serializable { }

    public @CheckForNull LoggedInUserData loggedInUserData;
    public @CheckForNull LoggedInApplicationData loggedInApplicationData;

    public static @Nonnull ServicePortalSession get() {
        return (ServicePortalSession) WebSession.get();
    }

    public ServicePortalSession(@Nonnull Request request) {
        super(request);
    }

    @Override public Locale getLocale() {
        return Locale.ENGLISH;
    }

    public @Nonnull LoggedInUserData getLoggedInUserDataOrThrow() {
        if (this.loggedInUserData == null) throw new IllegalStateException();
        return this.loggedInUserData;
    }

    public @Nonnull LoggedInApplicationData getLoggedInApplicationDataOrThrow() {
        if (this.loggedInApplicationData == null) throw new IllegalStateException();
        return this.loggedInApplicationData;
    }
}
