package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import endpoints.serviceportal.wicket.ServicePortalSession;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;

import javax.annotation.Nonnull;

public class AdminChangePasswordPage extends AbstractLoggedInAdminPage {

    boolean checkOldPassword = true;
    @Getter @Setter CleartextPassword oldPasswordField, newPassword1Field, newPassword2Field;
    
    public AdminChangePasswordPage() {
        var form = new Form("form");
        add(form);
        
        form.add(new FeedbackPanel("feedback"));
        form.add(new PasswordTextField("oldPassword",  new PropertyModel<>(this, "oldPasswordField")));
        form.add(new PasswordTextField("newPassword1", new PropertyModel<>(this, "newPassword1Field")));
        form.add(new PasswordTextField("newPassword2", new PropertyModel<>(this, "newPassword2Field")));

        form.add(new BookmarkablePageLink<>("cancel", AdminApplicationListPage.class));
        form.add(new Button("submit") {
            @Override public void onSubmit() {
                boolean success = ChangePasswordPage.onChangePassword(
                    checkOldPassword, oldPasswordField, newPassword1Field, newPassword2Field);
                if (success) setResponsePage(AdminApplicationListPage.class);
            }
        });
    }
    
    public static @Nonnull AdminChangePasswordPage newMandatoryChangePasswordPage() {
        var result = new AdminChangePasswordPage();
        result.get("form:oldPassword").setVisible(false);
        result.get("form:cancel").setVisible(false);
        result.get("changePassword").setVisible(false); // in top nav
        result.checkOldPassword = false;
        ServicePortalSession.get().info("You must change your password the first time you log in");
        return result;
    }
}
