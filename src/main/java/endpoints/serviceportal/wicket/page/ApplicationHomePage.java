package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.SneakyThrows;
import org.apache.wicket.markup.html.basic.Label;

import java.net.MalformedURLException;
import java.net.URL;

public class ApplicationHomePage extends AbstractLoggedInPage {

    @SneakyThrows({ConfigurationException.class, MalformedURLException.class})
    public ApplicationHomePage() {
        super(NavigationItem.ApplicationHomePage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var applicationName = getSession().getLoggedInDataOrThrow().application;
            var applicationUrl = new URL(getBaseUrl(), "/" + applicationName.name + "/");

            add(new Label("applicationDisplayName", getSession().getLoggedInDataOrThrow().applicationDisplayName));
            add(new Label("applicationName", applicationName.name));
            add(new Label("repository", DeploymentParameters.get().getGitRepository(applicationName).info));
            add(new Label("urlPreview", new URL(applicationUrl, "{endpoint}?environment=preview&{parameter}").toExternalForm()));
            add(new Label("urlLive", new URL(applicationUrl, "{endpoint}?{parameter}").toExternalForm()));
        }
    }
}
