package endpoints.serviceportal.wicket.panel;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.endpointmenu.EndpointMenuItemPanel;
import endpoints.serviceportal.wicket.page.ChooseApplicationPage;
import endpoints.serviceportal.wicket.page.LoginPage;
import lombok.RequiredArgsConstructor;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class NavigationPanel extends Panel {

    @RequiredArgsConstructor
    public enum NavigationType {
        mobile("uk-nav uk-nav-default uk-padding", true),
        desktop("uk-nav uk-nav-primary uk-padding", false);
        
        public final @Nonnull String cssClass;
        public final boolean logoutVisible;
    }

    @RequiredArgsConstructor
    public enum NavigationItem {
        ApplicationHomePage(false),
        RequestLogPage(false),
        PublishPage(false),
        CalculateHashPage(true),
        GenerateNewSecretKeyPage(true),
        ChangePasswordPage(true);
        
        public final boolean inSecuritySubMenu;
    }

    /**
     * @param navigationItem set to non-null to indicate a static menu item selected, such as ChangePasswordPage
     * @param endpointLeaf   set to non-null to indicate a custom endpoint menu item is selected
     */
    public NavigationPanel(
        @Nonnull DbTransaction tx, @Nonnull String wicketId, @Nonnull NavigationType type,
        @CheckForNull NavigationItem navigationItem, @CheckForNull MultiEnvironmentEndpointLeafMenuItem endpointLeaf
    ) {
        super(wicketId);
        
        var ul = new WebMarkupContainer("ul");
        ul.add(new AttributeModifier("class", type.cssClass));
        add(ul);
        
        var security = new WebMarkupContainer("security");
        security.add(new AttributeModifier("class", 
            (navigationItem != null && navigationItem.inSecuritySubMenu) ? "uk-active" : "uk-parent"));
        ul.add(security);

        for (var item : NavigationItem.values()) {
            var li = new WebMarkupContainer(item.name());
            li.add(new AttributeAppender("class", navigationItem == item ? "uk-active" : ""));
            (item.inSecuritySubMenu ? security : ul).add(li);
        }
        
        var mobileOnlyContainer = new WebMarkupContainer("mobileOnly");
        mobileOnlyContainer.setVisible(type.logoutVisible);
        ul.add(mobileOnlyContainer);

        ul.add(EndpointMenuItemPanel.newRootLiRepeater(tx, "endpointMenuFolder", "item", endpointLeaf));

        mobileOnlyContainer.add(new Link<Void>("change-application") {
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
        mobileOnlyContainer.add(new Link<Void>("logout") {
            @Override public void onClick() {
                ServicePortalSession.get().logoutAndInvalidateSession();
                setResponsePage(LoginPage.class);
            }
        });
    }
}
