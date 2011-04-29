package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.BCryptPassword;
import com.databasesandlife.util.CleartextPassword;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.model.PropertyModel;

import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN;

public class ChangePasswordPage extends AbstractLoggedInPage {

    @Getter @Setter CleartextPassword oldPasswordField, newPassword1Field, newPassword2Field;

    public ChangePasswordPage() {
        super(NavigationItem.ChangePasswordPage, null);

        add(new ServicePortalFeedbackPanel("feedback"));

        Form form = new Form<Void>("form") {
            @Override protected void onSubmit() {
                onChangePassword();
            }
        };
        add(form);

        form.add(new PasswordTextField("oldPassword",  new PropertyModel<>(this, "oldPasswordField")));
        form.add(new PasswordTextField("newPassword1", new PropertyModel<>(this, "newPassword1Field")));
        form.add(new PasswordTextField("newPassword2", new PropertyModel<>(this, "newPassword2Field")));
    }

    public void onChangePassword() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {

            var oldPassword = tx.jooq().select(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT)
                .from(SERVICE_PORTAL_LOGIN).where(SERVICE_PORTAL_LOGIN.USERNAME.eq(getSession().getLoggedInDataOrThrow().username))
                .fetchOne().value1();
            if ( ! oldPassword.is(oldPasswordField)) {
                error("Old password wrong");
                return;
            }

            if ( ! newPassword1Field.equals(newPassword2Field)) {
                error("New passwords differ");
                return;
            }

            tx.jooq().update(SERVICE_PORTAL_LOGIN)
                .set(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT, new BCryptPassword(newPassword1Field))
                .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(getSession().getLoggedInDataOrThrow().username))
                .execute();

            info("Password changed successfully");

            tx.commit();
        }
    }
}
