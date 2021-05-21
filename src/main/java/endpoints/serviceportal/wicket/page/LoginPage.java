package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.ServicePortalUsername;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.PropertyModel;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN_APPLICATION;

public class LoginPage extends AbstractPage {

    protected ServicePortalUsername username = null;
    protected CleartextPassword password = null;

    public LoginPage() {
        if (ServicePortalSession.get().isLoggedIn()) setResponsePage(ApplicationHomePage.class);

        var form = new StatelessForm<Object>("form") { @Override public void onSubmit() { LoginPage.this.onSubmit(); } };
        add(form);

        form.add(new ServicePortalFeedbackPanel("feedback"));
        form.add(new TextField<>("username", new PropertyModel<>(this, "username")).setRequired(true));
        form.add(new PasswordTextField("password", new PropertyModel<>(this, "password")));
    }

    protected void onSubmit() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var databasePassword = tx.jooq()
                .select(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT)
                .from(SERVICE_PORTAL_LOGIN)
                .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(username))
                .fetchOne(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT);

            if (databasePassword == null || ! databasePassword.is(password)) {
                error("User doesn't exist or password wrong");
                return;
            }

            var applications = tx.jooq()
                .select(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME, APPLICATION_CONFIG.DISPLAY_NAME)
                .from(SERVICE_PORTAL_LOGIN_APPLICATION)
                .join(APPLICATION_CONFIG).on(APPLICATION_CONFIG.APPLICATION_NAME.eq(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME))
                .where(SERVICE_PORTAL_LOGIN_APPLICATION.USERNAME.eq(username))
                .fetch();
            
            if (applications.isEmpty()) {
                error("No applications configured for this user");
            }
            else if (applications.size() == 1) {
                ServicePortalSession.get().login(username, applications.get(0).value1(), applications.get(0).value2(), false);
                continueToOriginalDestination();
                setResponsePage(ApplicationHomePage.class);
            }
            else {
                setResponsePage(new ChooseApplicationPage(tx, username));
            }

            tx.commit();
        }
    }
}
