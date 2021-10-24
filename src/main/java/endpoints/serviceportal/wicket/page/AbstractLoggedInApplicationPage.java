package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.NavigationPanel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.SneakyThrows;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractLoggedInApplicationPage extends AbstractPage {

    public AbstractLoggedInApplicationPage(
        @CheckForNull NavigationItem navigationItem, 
        @CheckForNull MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem endpointLeaf
    ) {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            if (ServicePortalSession.get().loggedInApplicationData == null)
                throw new RestartResponseAtInterceptPageException(
                    ServicePortalSession.get().loggedInUserData == null ? LoginPage.class : ChooseApplicationPage.class);
    
            add(new NavigationPanel(tx, "navigationMobile",  true,  navigationItem, endpointLeaf));
            add(new NavigationPanel(tx, "navigationDesktop", false, navigationItem, endpointLeaf));
            add(new BookmarkablePageLink<>("change-password", ChangePasswordPage.class));
            add(new Link<Void>("change-application") {
                @Override public void onClick() {
                    ServicePortalSession.get().loggedInApplicationData = null;
                    getRequestCycle().setResponsePage(ServicePortalSession.get().getLoggedInUserDataOrThrow().isAdmin 
                        ? AdminApplicationListPage.class : ChooseApplicationPage.class);
                }
                @Override public boolean isVisible() {
                    var login = ServicePortalSession.get().getLoggedInUserDataOrThrow();
                    return login.isAdmin || login.moreThanOneApplication;
                }
            });
            add(new Link<Void>("logout") {
                @Override public void onClick() {
                    ServicePortalSession.get().invalidate();
                    setResponsePage(LoginPage.class);
                }
            }.add(new Label("username",  ServicePortalSession.get().getLoggedInUserDataOrThrow().username)));
            
            tx.commit();
        }
    }

    @SneakyThrows(MalformedURLException.class)
    public @Nonnull URL getBaseUrl() {
        var req = (HttpServletRequest) getRequest().getContainerRequest();
        var ourUrl = new URL(req.getRequestURL().toString());
        return new URL(ourUrl.getProtocol(), ourUrl.getHost(), ourUrl.getPort(), "");
    }
}
