package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.model.PropertyModel;

import javax.annotation.Nonnull;

public class AdminChangePasswordPage extends AbstractLoggedInAdminPage {

    boolean checkOldPassword = true;
    @Getter @Setter CleartextPassword oldPasswordField, newPassword1Field, newPassword2Field;
    
    @SuppressWarnings("PropertyModel")
    public AdminChangePasswordPage() {
        add(new ServicePortalFeedbackPanel("feedback"));
        
        var form = new Form<>("form") {
            @Override public void onSubmit() {
                boolean success = ChangePasswordPage.onChangePassword(
                    checkOldPassword, oldPasswordField, newPassword1Field, newPassword2Field);
                if (success) setResponsePage(AdminApplicationListPage.class);
            }
        };
        add(form);
        
        form.add(new PasswordTextField("oldPassword",  new PropertyModel<>(this, "oldPasswordField")));
        form.add(new PasswordTextField("newPassword1", new PropertyModel<>(this, "newPassword1Field")));
        form.add(new PasswordTextField("newPassword2", new PropertyModel<>(this, "newPassword2Field")));
        form.add(new BookmarkablePageLink<>("cancel", AdminApplicationListPage.class));
    }
    
    public static @Nonnull AdminChangePasswordPage newMandatoryChangePasswordPage() {
        var result = new AdminChangePasswordPage();
        result.get("form:oldPassword").setVisible(false);
        result.get("form:cancel").setVisible(false);
        result.get("desktop.change-password").setVisible(false);
        result.get("mobile.change-password").setVisible(false);
        result.checkOldPassword = false;
        ServicePortalSession.get().info("You must change your password the first time you log in");
        return result;
    }
}
