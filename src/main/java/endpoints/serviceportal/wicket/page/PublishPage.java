package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.GitRevision;
import endpoints.PublishEnvironment;
import endpoints.PublishProcess;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import org.apache.log4j.Logger;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.LoadableDetachableModel;

import javax.annotation.Nonnull;

import java.util.Map;
import java.util.Optional;

import static endpoints.PublishProcess.setApplicationToPublished;
import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;

public class PublishPage extends AbstractLoggedInApplicationPage {

    @SuppressWarnings("WicketForgeJavaIdInspection")
    public PublishPage() {
        super(NavigationItem.PublishPage, null);

        for (var environment : PublishEnvironment.values()) 
            add(new Label(environment.name() + ".revision", () -> {
                try (var tx = DeploymentParameters.get().newDbTransaction()) {
                    var currentRevision = tx.jooq()
                        .select(APPLICATION_PUBLISH.REVISION)
                        .from(APPLICATION_PUBLISH)
                        .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(getSession().getLoggedInApplicationDataOrThrow().application))
                        .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                        .fetchOne(APPLICATION_PUBLISH.REVISION);
                    return currentRevision == null ? "Not published" : currentRevision.getAbbreviated();
                }
            }));

        add(new ServicePortalFeedbackPanel("feedback"));
        add(new Link<Void>("preview.publish") { @Override public void onClick() { onPublish(PublishEnvironment.preview); }});
        add(new Link<Void>("promote") { @Override public void onClick() { onPromote(); }});
    }

    public void onPublish(@Nonnull PublishEnvironment environment) {
        var application = getSession().getLoggedInApplicationDataOrThrow().application;
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var publish = new PublishProcess(application, environment);
            var revision = publish.publish(tx, line -> Logger.getLogger(PublishPage.class).info(line));
            var envText = environment == PublishEnvironment.live ? "" : " to " + environment.name() + " environment";
            getSession().info("Successfully published '" + getSession().getLoggedInApplicationDataOrThrow().applicationDisplayName + "'" + envText);
            setResponsePage(PublishPage.class); // Cause navigation to reload (e.g. custom menu items changed after publish)
            tx.commit();
        }
        catch (PublishProcess.ApplicationInvalidException e) {
            Logger.getLogger(getClass()).warn("Publish of '" + application.name + "' on '" + environment.name() + "' failed", e);
            error(e.getMessage());
        }
    }

    public void onPromote() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var currentPreviewRevision = tx.jooq()
                .select(APPLICATION_PUBLISH.REVISION)
                .from(APPLICATION_PUBLISH)
                .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(getSession().getLoggedInApplicationDataOrThrow().application))
                .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(PublishEnvironment.preview))
                .fetchOne(APPLICATION_PUBLISH.REVISION);

            if (currentPreviewRevision == null) { error("Not published yet to preview"); return; }

            setApplicationToPublished(tx, getSession().getLoggedInApplicationDataOrThrow().application,
                PublishEnvironment.live, currentPreviewRevision);

            getSession().info("Successfully promoted '" + getSession().getLoggedInApplicationDataOrThrow().applicationDisplayName
                + "' from preview environment to live");
            setResponsePage(PublishPage.class); // Cause navigation to reload (e.g. custom menu items changed after publish)

            tx.commit();
        }
    }
}
