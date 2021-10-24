package endpoints.serviceportal.wicket.panel;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.endpointmenu.EndpointMenuItemPanel;
import endpoints.serviceportal.wicket.page.AdminApplicationListPage;
import endpoints.serviceportal.wicket.page.ChooseApplicationPage;
import endpoints.serviceportal.wicket.page.LoginPage;
import lombok.RequiredArgsConstructor;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * This represents the &lt;ul&gt; tag.
 */
public class NavigationPanel extends WebMarkupContainer {

    @RequiredArgsConstructor
    public enum NavigationItem {
        ApplicationHomePage(false),
        RequestLogPage(false),
        PublishPage(false),
        CalculateHashPage(false),
        ChangePasswordPage(true);
        
        public final boolean mobileOnly;
    }

    /**
     * @param navigationItem set to non-null to indicate a static menu item selected, such as ChangePasswordPage
     * @param endpointLeaf   set to non-null to indicate a custom endpoint menu item is selected
     */
    public NavigationPanel(
        @Nonnull DbTransaction tx, @Nonnull String wicketId, boolean isMobile,
        @CheckForNull NavigationItem navigationItem, @CheckForNull MultiEnvironmentEndpointLeafMenuItem endpointLeaf
    ) {
        super(wicketId);

        add(new Label("applicationName",
            ServicePortalSession.get().getLoggedInApplicationDataOrThrow().applicationDisplayName)).setVisible(isMobile == false);
        
        for (var item : NavigationItem.values()) {
            if ( ! item.mobileOnly || isMobile) {
                var li = new WebMarkupContainer(item.name());
                li.add(new AttributeAppender("class", navigationItem == item ? "uk-active" : ""));
                add(li);
            }
        }
        
        add(EndpointMenuItemPanel.newRootLiRepeater(tx, "endpointMenuFolder", "item", endpointLeaf));

        add(new Link<Void>("change-application") {
            @Override public void onClick() {
                ServicePortalSession.get().loggedInApplicationData = null;
                getRequestCycle().setResponsePage(ServicePortalSession.get().getLoggedInUserDataOrThrow().isAdmin
                    ? AdminApplicationListPage.class : ChooseApplicationPage.class);
            }
            @Override public boolean isVisible() {
                var login = ServicePortalSession.get().getLoggedInUserDataOrThrow();
                return isMobile && (login.isAdmin || login.moreThanOneApplication);
            }
        });
        add(new Link<Void>("logout") {
            @Override public void onClick() {
                ServicePortalSession.get().invalidate();
                setResponsePage(LoginPage.class);
            }
            @Override public boolean isVisible() {
                return isMobile;
            }
        });
    }
}
