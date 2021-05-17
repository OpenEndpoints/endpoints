package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem;
import endpoints.serviceportal.wicket.panel.NavigationPanel;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

import static endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationType.desktop;
import static endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationType.mobile;

public abstract class AbstractLoggedInPage extends AbstractPage {

    public AbstractLoggedInPage(
        @CheckForNull NavigationItem navigationItem, 
        @CheckForNull MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem endpointLeaf
    ) {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            if ( ! ServicePortalSession.get().isLoggedIn()) {
                Logger.getLogger(getClass()).info("User not logged in but trying to access " + getClass() + ": will intercept to login page");
                throw new RestartResponseAtInterceptPageException(LoginPage.class);
            }
    
            add(new NavigationPanel(tx, "navigationMobile",  mobile,  navigationItem, endpointLeaf));
            add(new NavigationPanel(tx, "navigationDesktop", desktop, navigationItem, endpointLeaf));
            add(new Label("applicationDesktop", ServicePortalSession.get().getLoggedInDataOrThrow().applicationDisplayName));
            add(new Label("applicationMobile",  ServicePortalSession.get().getLoggedInDataOrThrow().applicationDisplayName));
            add(new Label("usernameDesktop", ServicePortalSession.get().getLoggedInDataOrThrow().username));
            add(new Label("usernameMobile",  ServicePortalSession.get().getLoggedInDataOrThrow().username));
            add(new Link<Void>("change-application") {
                @Override public void onClick() {
                    try (var tx = DeploymentParameters.get().newDbTransaction()) {
                        var username = ServicePortalSession.get().getLoggedInDataOrThrow().username;
                        ServicePortalSession.get().logoutWithoutInvalidatingSession();
                        setResponsePage(new ChooseApplicationPage(tx, username));
                    }
                }
                @Override public boolean isVisible() {
                    if ( ! ServicePortalSession.get().getLoggedInDataOrThrow().moreThanOneApplication) return false;
                    return super.isVisible();
                }
            });
            add(new Link<Void>("logout") {
                @Override public void onClick() {
                    ServicePortalSession.get().logoutAndInvalidateSession();
                    setResponsePage(LoginPage.class);
                }
            });
            
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
