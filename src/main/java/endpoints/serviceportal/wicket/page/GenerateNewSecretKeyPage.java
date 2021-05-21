package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.GitApplicationRepository.RepositoryCommandFailedException;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.apache.wicket.markup.html.form.Form;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GenerateNewSecretKeyPage extends AbstractLoggedInPage {

    public GenerateNewSecretKeyPage() {
        super(NavigationItem.GenerateNewSecretKeyPage, null);

        add(new ServicePortalFeedbackPanel("feedback"));
        add(new Form<Void>("form") { @Override public void onSubmit() { GenerateNewSecretKeyPage.this.onSubmit(); } });
    }

    @SneakyThrows(ConfigurationException.class)
    protected void onSubmit() {
        try {
            var application = getSession().getLoggedInDataOrThrow().application;
            var repo = DeploymentParameters.get().getGitRepository(application);
            repo.checkoutAlterAndCommit(application,
                getSession().getLoggedInDataOrThrow().username.username + " (Endpoints Service Portal User)",
                "Generate new secret key (via Endpoints Service Portal)",
                gitCheckoutDirectory -> writeSecretKey(new File(gitCheckoutDirectory, "security.xml")));
            info("A new secret key has been created in a new (not yet published) revision of your configuration.");
        }
        catch (RepositoryCommandFailedException e) {
            Logger.getLogger(getClass()).error("Couldn't generate & push new secure key", e);
            error(e.getMessage());
        }
    }

    @SneakyThrows({TransformerConfigurationException.class, TransformerException.class, IOException.class})
    protected void writeSecretKey(File securityXml) {
        var newKey = CleartextPassword.newRandom(20);

        var doc = DomParser.newDocumentBuilder().newDocument();

        var secretKeyElement = doc.createElement("secret-key");
        secretKeyElement.setTextContent(newKey.getCleartext());

        var securityElement = doc.createElement("security");
        securityElement.appendChild(secretKeyElement);
        doc.appendChild(securityElement);

        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (var w = new FileOutputStream(securityXml)) {
            transformer.transform(new DOMSource(doc), new StreamResult(w));
        }
    }
}
