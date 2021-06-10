package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import org.apache.wicket.RestartResponseAtInterceptPageException;

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
    }
}
