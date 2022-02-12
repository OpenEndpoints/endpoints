package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.BCryptPassword;
import com.databasesandlife.util.gwtsafe.CleartextPassword;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.model.PropertyModel;

import javax.annotation.Nonnull;

import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN;

public class ChangePasswordPage extends AbstractLoggedInApplicationPage {

    @Getter @Setter CleartextPassword oldPasswordField, newPassword1Field, newPassword2Field;

    public ChangePasswordPage() {
        super(NavigationItem.ChangePasswordPage, null);

        add(new ServicePortalFeedbackPanel("feedback"));

        Form form = new Form<Void>("form") {
            @Override protected void onSubmit() {
                onChangePassword(true, oldPasswordField, newPassword1Field, newPassword2Field);
            }
        };
        add(form);

        form.add(new PasswordTextField("oldPassword",  new PropertyModel<>(this, "oldPasswordField")));
        form.add(new PasswordTextField("newPassword1", new PropertyModel<>(this, "newPassword1Field")));
        form.add(new PasswordTextField("newPassword2", new PropertyModel<>(this, "newPassword2Field")));
    }

    public static boolean onChangePassword(
        boolean checkOldPassword, @Nonnull CleartextPassword oldPasswordField, 
        @Nonnull CleartextPassword newPassword1Field, @Nonnull CleartextPassword newPassword2Field
    ) {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var session = ServicePortalSession.get();

            if (checkOldPassword) {
                var oldPassword = tx.jooq()
                    .select(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT)
                    .from(SERVICE_PORTAL_LOGIN)
                    .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(session.getLoggedInUserDataOrThrow().username))
                    .fetchSingle().value1();
                if ( ! oldPassword.is(oldPasswordField)) {
                    session.error("Old password wrong");
                    return false;
                }
            }

            if ( ! newPassword1Field.equals(newPassword2Field)) {
                session.error("New passwords differ");
                return false;
            }

            tx.jooq().update(SERVICE_PORTAL_LOGIN)
                .set(SERVICE_PORTAL_LOGIN.PASSWORD_BCRYPT, new BCryptPassword(newPassword1Field))
                .set(SERVICE_PORTAL_LOGIN.MUST_CHANGE_PASSWORD, false)
                .where(SERVICE_PORTAL_LOGIN.USERNAME.eq(session.getLoggedInUserDataOrThrow().username))
                .execute();

            session.info("Password changed successfully");

            tx.commit();
            return true;
        }
    }
}
