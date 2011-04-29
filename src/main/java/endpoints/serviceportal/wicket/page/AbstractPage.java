package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.ServicePortalSession;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static java.time.ZoneOffset.UTC;

public class AbstractPage extends WebPage {

    public ServicePortalSession getSession() {
        return (ServicePortalSession) super.getSession();
    }

    public AbstractPage() {
        add(new Label("environment", DeploymentParameters.get().servicePortalEnvironmentDisplayName)
            .setVisible(DeploymentParameters.get().servicePortalEnvironmentDisplayName != null));
        add(new Label("year", Instant.now().atZone(UTC).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy"))));
    }
}
