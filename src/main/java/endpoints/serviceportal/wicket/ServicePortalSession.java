package endpoints.serviceportal.wicket;

import endpoints.config.ApplicationName;
import endpoints.serviceportal.ServicePortalUsername;
import lombok.AllArgsConstructor;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.request.Request;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.ZoneId;
import java.util.Locale;

public class ServicePortalSession extends WebSession {

    @AllArgsConstructor
    public static final class LoggedInData implements Serializable {
        public final @Nonnull ServicePortalUsername username;
        public final @Nonnull ApplicationName application;
        public final @Nonnull String applicationDisplayName;
    }

    protected @CheckForNull LoggedInData loggedInData;

    public static @Nonnull ServicePortalSession get() {
        return (ServicePortalSession) WebSession.get();
    }

    public ServicePortalSession(@Nonnull Request request) {
        super(request);
    }

    @Override public Locale getLocale() {
        return Locale.ENGLISH;
    }

    public void login(
        @Nonnull ServicePortalUsername username, @Nonnull ApplicationName application, @Nonnull String applicationDisplayName
    ) {
        bind();
        this.loggedInData = new LoggedInData(username, application, applicationDisplayName);
    }

    public @Nonnull LoggedInData getLoggedInDataOrThrow() {
        if (this.loggedInData == null) throw new IllegalStateException();
        return this.loggedInData;
    }

    public boolean isLoggedIn() { return this.loggedInData != null; }
    public void logoutAndInvalidateSession() {
        invalidate();
    }
    public void logoutWithoutInvalidatingSession() {
        this.loggedInData = null;
    }
}
