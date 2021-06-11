package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.ServicePortalUsername;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.PropertyModel;
import org.jooq.Field;

import javax.annotation.CheckForNull;

import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN_APPLICATION;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectCount;

// Tests:
//   - Initial page is the login page
//   - Log in to a one-application non-admin user
//     - See application page
//     - Then go to a different application page
//     - Go to / and see you're at the application page
//     - No "change application" link
//     - Log out, go to app page, you're still logged out
//     - Log out, go to choose app page, you're still logged out
//   - Log in to a multi-application non-admin user
//     - See choose application page
//     - Go to /, see back at application screen
//     - Choose an application, you are viewing the application
//     - "Change application link" visible, use it, choose different app
//     - Log out, go to app page, you're still logged out
//     - Log out, go to choose app page, you're still logged out
//   - Log in to admin user
//     - See admin page
//     - Go to /, back at admin page
//     - Log out, go to choose app page, you're still logged out
//     - Log out, go to admin page, you're still logged out
//   - Log out, Go to a particular application page, log in to one-app non-admin, you're at the page
//   - Log out, Go to a particular application page, log in to multi-app non-admin, select app, you're at the page
//   - Log out, GO to a paritulcar admin page, log in as admin, you're at the page

public class LoginPage extends AbstractPage {

    protected ServicePortalUsername username = null;
    protected CleartextPassword password = null;

    public LoginPage() {
        if (getSession().loggedInUserData != null) {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                throwRedirectToPageAfterLogin(tx);
                tx.commit();
            }
        }

        var form = new StatelessForm<Object>("form") { @Override public void onSubmit() { LoginPage.this.onSubmit(); } };
        add(form);

        form.add(new ServicePortalFeedbackPanel("feedback"));
        form.add(new TextField<>("username", new PropertyModel<>(this, "username")).setRequired(true));
        form.add(new PasswordTextField("password", new PropertyModel<>(this, "password")));
    }

    protected void onSubmit() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            if (DeploymentParameters.get().isSingleApplicationMode()) {
                error("Cannot log in to Service Portal when deployment is 'single application mode'");
                return;
            }
            
            @CheckForNull var login = tx.jooq()
                .selectFrom(SERVICE_PORTAL_LOGIN)
                .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(username))
                .fetchOne();

            if (login == null || ! login.getPasswordBcrypt().is(password)) {
                error("User doesn't exist or password wrong");
                return;
            }

            var applicationCount = tx.jooq().selectCount().from(SERVICE_PORTAL_LOGIN_APPLICATION)
                .where(SERVICE_PORTAL_LOGIN_APPLICATION.USERNAME.eq(username)).fetchSingle().value1();
            
            getSession().bind();
            getSession().loggedInUserData = new ServicePortalSession.LoggedInUserData(username, login.getAdmin(),
                applicationCount > 1);
            
            throwRedirectToPageAfterLogin(tx);
        }
    }
}
