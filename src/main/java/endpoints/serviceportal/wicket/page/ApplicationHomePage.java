package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.GitApplicationRepository;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import lombok.SneakyThrows;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URL;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_IDS;
import static org.jooq.impl.DSL.select;

public class ApplicationHomePage extends AbstractLoggedInApplicationPage {

    private final @Nonnull ApplicationConfigRecord app;

    @SneakyThrows(MalformedURLException.class)
    public ApplicationHomePage() {
        super(NavigationItem.ApplicationHomePage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var applicationName = getSession().getLoggedInApplicationDataOrThrow().application();
            var applicationUrl = new URL(getBaseUrl(), "/" + applicationName.name() + "/");
            var applicationRepo = GitApplicationRepository.fetch(tx, applicationName);

            app = tx.jooq().fetchSingle(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName));

            add(new ServicePortalFeedbackPanel("feedback"));
            add(new Label("applicationName", applicationName.name()));
            add(new Label("repository", applicationRepo.url));
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
            var applicationName = getSession().getLoggedInApplicationDataOrThrow().application();
            
            tx.jooq()
                .update(REQUEST_LOG)
                .setNull(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT)
                .setNull(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT)
                .setNull(REQUEST_LOG.REQUEST_CONTENT_TYPE)
                .setNull(REQUEST_LOG.REQUEST_BODY)
                .where(REQUEST_LOG.REQUEST_ID.in(
                    select(REQUEST_LOG_IDS.REQUEST_ID)
                    .from(REQUEST_LOG_IDS)
                    .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))))
                .execute();
            
            success("Debug entries successfully removed from all requests for application '" + applicationName.name() + "'");
            
            tx.commit();
        }
    }
}
