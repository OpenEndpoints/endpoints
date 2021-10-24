package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.ServicePortalSession;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN_APPLICATION;
import static java.time.ZoneOffset.UTC;

public class AbstractPage extends WebPage {

    public ServicePortalSession getSession() {
        return (ServicePortalSession) super.getSession();
    }

    public AbstractPage() {
        add(new Label("environment", DeploymentParameters.get().servicePortalEnvironmentDisplayName)
            .setVisible(DeploymentParameters.get().servicePortalEnvironmentDisplayName != null));
    }

    protected void throwRedirectToPageAfterLogin(@Nonnull DbTransaction tx) {
        if (getSession().loggedInUserData == null) {
            throw new RestartResponseException(LoginPage.class);
        }

        if (getSession().loggedInApplicationData != null) {
            continueToOriginalDestination();
            throw new RestartResponseException(ApplicationHomePage.class);
        }

        if (getSession().loggedInUserData.isAdmin) {
            var mustChangePassword = tx.jooq().select(SERVICE_PORTAL_LOGIN.MUST_CHANGE_PASSWORD).from(SERVICE_PORTAL_LOGIN)
                .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(getSession().loggedInUserData.username)).fetchSingle().value1();
            if (mustChangePassword) throw new RestartResponseException(AdminChangePasswordPage.newMandatoryChangePasswordPage());

            continueToOriginalDestination();
            throw new RestartResponseException(AdminApplicationListPage.class);
        }

        var applications = tx.jooq()
            .select(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME, APPLICATION_CONFIG.DISPLAY_NAME)
            .from(SERVICE_PORTAL_LOGIN_APPLICATION)
            .join(APPLICATION_CONFIG).on(APPLICATION_CONFIG.APPLICATION_NAME.eq(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME))
            .where(SERVICE_PORTAL_LOGIN_APPLICATION.USERNAME.eq(getSession().getLoggedInUserDataOrThrow().username))
            .fetch();
        if (applications.size() > 1) {
            getSession().loggedInUserData.moreThanOneApplication = true;
            throw new RestartResponseException(ChooseApplicationPage.class);
        }
        else if (applications.isEmpty()) {
            error("No applications configured for this user");
            getSession().loggedInUserData = null;
            throw new RestartResponseException(LoginPage.class);
        }
        else {
            getSession().loggedInUserData.moreThanOneApplication = false;
            ServicePortalSession.get().loggedInApplicationData =
                new ServicePortalSession.LoggedInApplicationData(applications.get(0).value1(), applications.get(0).value2());
            continueToOriginalDestination();
            throw new RestartResponseException(ApplicationHomePage.class);
        }
    }

}
