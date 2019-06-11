package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.generated.jooq.Tables;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import lombok.SneakyThrows;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URL;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;

public class ApplicationHomePage extends AbstractLoggedInPage {

    private final @Nonnull ApplicationConfigRecord app;

    @SneakyThrows({ConfigurationException.class, MalformedURLException.class})
    public ApplicationHomePage() {
        super(NavigationItem.ApplicationHomePage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var applicationName = getSession().getLoggedInDataOrThrow().application;
            var applicationUrl = new URL(getBaseUrl(), "/" + applicationName.name + "/");

            app = tx.jooq().fetchOne(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName));

            add(new ServicePortalFeedbackPanel("feedback"));
            add(new Label("applicationDisplayName", getSession().getLoggedInDataOrThrow().applicationDisplayName));
            add(new Label("applicationName", applicationName.name));
            add(new Label("repository", DeploymentParameters.get().getGitRepository(applicationName).info));
            add(new Label("urlPreview", new URL(applicationUrl, "{endpoint}?environment=preview&{parameter}").toExternalForm()));
            add(new Label("urlLive", new URL(applicationUrl, "{endpoint}?{parameter}").toExternalForm()));
            add(new Label("currentDebugAllowed", () -> app.getDebugAllowed() ? "Debug Allowed" : "Debug Not Allowed"));
            add(new Link<>("toggleDebugAllowed") { @Override public void onClick() { onToggleDebugAllowed(); }});
            add(new Link<>("clearDebugLog") { @Override public void onClick() { clearDebugLog(); }});
        }
    }
    
    public void onToggleDebugAllowed() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            app.setDebugAllowed( ! app.getDebugAllowed());
            app.attach(tx.jooq().configuration());
            app.update();
            
            tx.commit();
        }
    }

    public void clearDebugLog() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var applicationName = getSession().getLoggedInDataOrThrow().application;
            
            tx.jooq()
                .update(REQUEST_LOG)
                .set(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT, (Element) null)
                .set(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT, (Element) null)
                .where(REQUEST_LOG.APPLICATION.eq(applicationName))
                .execute();
            
            success("Debug entries successfully removed from all requests for application '" + applicationName.name + "'");
            
            tx.commit();
        }
    }
}
