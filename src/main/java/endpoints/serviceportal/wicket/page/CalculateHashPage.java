package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.WebEncodingUtils;
import com.databasesandlife.util.wicket.LambdaDisplayValueChoiceRenderer;
import com.databasesandlife.util.wicket.MapModel;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.ApplicationFactory.ApplicationNotFoundException;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.NodeName;
import endpoints.config.ParameterName;
import endpoints.serviceportal.wicket.model.EndpointNamesModel;
import endpoints.serviceportal.wicket.model.ParametersModel;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LambdaModel;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static java.util.Arrays.asList;

public class CalculateHashPage extends AbstractLoggedInApplicationPage {

    protected @Nonnull @Getter @Setter PublishEnvironment environment = PublishEnvironment.live;
    protected @CheckForNull @Getter @Setter NodeName endpoint = null;
    protected @Nonnull final Map<ParameterName, String> parameters = new HashMap<>();
    protected @CheckForNull @Getter String generatedHash = null, generatedUrl = null;

    public CalculateHashPage() {
        super(NavigationItem.CalculateHashPage, null);

        add(new ServicePortalFeedbackPanel("feedback"));

        var form = new Form<>("form");
        add(form);

        var endpointsNameModel = new EndpointNamesModel(this::getEnvironment);
        var parametersModel = new ParametersModel(this::getEnvironment, this::getEndpoint);

        form.add(new DropDownChoice<>("environment", LambdaModel.of(this::getEnvironment, this::setEnvironment),
            asList(PublishEnvironment.values()), new EnumChoiceRenderer<>(this)));
        form.add(new DropDownChoice<>("endpoint", LambdaModel.of(this::getEndpoint, this::setEndpoint),
            endpointsNameModel, new LambdaDisplayValueChoiceRenderer<>(e -> e.name)));
        form.add(new Button("refresh") { @Override public void onSubmit() { 
            endpointsNameModel.refresh(); parametersModel.refresh();
            generatedHash = generatedUrl = null;
        }});
        form.add(new Button("calculate") { @Override public void onSubmit() { computeOutput(); }});

        form.add(new ListView<>("row", parametersModel) {
            @Override protected void populateItem(@Nonnull ListItem<ParameterName> item) {
                var param = item.getModelObject();
                item.add(new Label("label", param.name));
                item.add(new TextField<>("value", new MapModel<>(parameters, param))
                    .setConvertEmptyInputStringToNull(false));
            }
        });
        
        var result = new WebMarkupContainer("result") {
            @Override public boolean isVisible() {
                return generatedHash != null && generatedUrl != null;
            }
        };
        add(result);

        result.add(new Label("generatedHash", this::getGeneratedHash));
        result.add(new Label("generatedUrl", this::getGeneratedUrl));
    }

    @SneakyThrows(MalformedURLException.class)
    public void computeOutput() {
        generatedHash = null; // So that in case there's an error, an old hash is not displayed
        
        if (endpoint == null) { error("Please select an endpoint"); return; }

        try (var tx = DeploymentParameters.get().newDbTransaction(); var ignored = new Timer("computeOutput")) {
            var applicationName = ServicePortalSession.get().getLoggedInApplicationDataOrThrow().application();
            var application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment);
            var endpointDefn = application.getEndpoints().findEndpointOrThrow(endpoint);
            var hashDefn = endpointDefn.parametersForHash;
            generatedHash = hashDefn.calculateHash(application.getSecretKeys()[0], environment, endpoint, parameters);

            var urlGetParams = new HashMap<String, String>();
            for (var e : parameters.entrySet()) urlGetParams.put(e.getKey().getName(), e.getValue());
            urlGetParams.put("hash", generatedHash);
            if (environment != PublishEnvironment.getDefault()) urlGetParams.put("environment", environment.name());
            var applicationUrl = new URL(getBaseUrl(), "/" + applicationName.name + "/" + endpoint.name);
            generatedUrl = applicationUrl + "?" + WebEncodingUtils.encodeGetParameters(urlGetParams);
        }
        catch (ApplicationNotFoundException e) { error("Select a valid environment"); }
        catch (NodeNotFoundException e) { error("Select a valid endpoint"); }
    }
}
