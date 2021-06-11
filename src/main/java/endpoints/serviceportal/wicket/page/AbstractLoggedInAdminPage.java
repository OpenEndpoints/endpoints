package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.ServicePortalSession;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;

public abstract class AbstractLoggedInAdminPage extends AbstractPage {

    public AbstractLoggedInAdminPage() {
        if (getSession().loggedInUserData == null) 
            throw new RestartResponseAtInterceptPageException(LoginPage.class);
        
        if ( ! getSession().loggedInUserData.isAdmin) {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                throwRedirectToPageAfterLogin(tx);
                tx.commit();
            }
        }
        
        add(new Label("username", getSession().loggedInUserData.username));
        add(new BookmarkablePageLink<>("changePassword", AdminChangePasswordPage.class));
        add(new Link<>("logout") {
            @Override public void onClick() {
                ServicePortalSession.get().invalidate();
                setResponsePage(LoginPage.class);
            }
        });
    }
}
