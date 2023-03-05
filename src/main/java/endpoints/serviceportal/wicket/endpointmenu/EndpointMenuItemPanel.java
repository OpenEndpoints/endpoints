package endpoints.serviceportal.wicket.endpointmenu;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.ApplicationFactory;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointMenuFolder;
import endpoints.serviceportal.wicket.ServicePortalSession;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class EndpointMenuItemPanel extends Panel {
    
    public EndpointMenuItemPanel(
        @Nonnull String wicketId, 
        @CheckForNull MultiEnvironmentEndpointLeafMenuItem selected,
        @Nonnull MultiEnvironmentEndpointMenuItem item,
        boolean isTopLevel
    ) {
        super(wicketId);

        var leafLink = new Link<Void>("link") {
            @Override public void onClick() {
                var leaf = ((MultiEnvironmentEndpointLeafMenuItem) item);
                var environment = leaf.getDefaultEnvironment();
                setResponsePage(EndpointPage.newPage(environment, leaf));
            }
        };
        leafLink.add(new WebMarkupContainer("non-top-level-symbol").setVisible( ! isTopLevel));
        leafLink.add(new Label("name", item.menuItemName));

        var leafLi = new WebMarkupContainer("leaf");
        leafLi.setVisible(item instanceof MultiEnvironmentEndpointLeafMenuItem);
        leafLi.add(new AttributeAppender("class", item.contains(selected) ? "uk-active" : ""));
        leafLi.add(leafLink);
        add(leafLi);

        var folderLi = new WebMarkupContainer("folder");
        folderLi.setVisible(item instanceof MultiEnvironmentEndpointMenuFolder);
        folderLi.add(new WebMarkupContainer("non-top-level-symbol").setVisible( ! isTopLevel));
        folderLi.add(new Label("name", item.menuItemName));
        folderLi.add(new ListView<>("children",
            () -> {
                assert item instanceof MultiEnvironmentEndpointMenuFolder;  // "folder" not visible if not this class
                return ((MultiEnvironmentEndpointMenuFolder)item).children;
            }) {
            @Override protected void populateItem(ListItem<MultiEnvironmentEndpointMenuItem> item) {
                item.add(new EndpointMenuItemPanel("child", selected, item.getModelObject(), false));
            }
        });
        folderLi.add(new AttributeModifier("class", item.contains(selected) ? "uk-active" : "uk-parent"));
        add(folderLi);
    }
    
    /** Creates root folder */
    public static ListView<MultiEnvironmentEndpointMenuItem> newRootLiRepeater(
        @Nonnull DbTransaction tx, @Nonnull String containerWicketId, @Nonnull String liWicketId,
        @CheckForNull MultiEnvironmentEndpointLeafMenuItem selected
    ) {
        var applicationName = ServicePortalSession.get().getLoggedInApplicationDataOrThrow().application();
        var displayItems = new MultiEnvironmentEndpointMenuFolder("Not displayed");
        for (var environment : PublishEnvironment.values()) {
            try {
                var application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment);
                displayItems.mergeChildren(environment, application.getServicePortalEndpointMenuItems());
            }
            catch (ApplicationFactory.ApplicationNotFoundException ignored) { }
        }
        displayItems.prune();
        
        return new ListView<>(containerWicketId, displayItems.children) {
            @Override protected void populateItem(ListItem<MultiEnvironmentEndpointMenuItem> item) {
                item.add(new EndpointMenuItemPanel(liWicketId, selected, item.getModelObject(), true));
            }
        };
    }
}
