package endpoints.serviceportal.wicket.model;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.wicket.CachingFutureModel;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.*;
import endpoints.serviceportal.wicket.ServicePortalSession;
import lombok.RequiredArgsConstructor;
import org.danekja.java.util.function.serializable.SerializableSupplier;

import javax.annotation.Nonnull;
import java.util.ArrayList;

@RequiredArgsConstructor
public class ParametersModel extends CachingFutureModel<ArrayList<ParameterName>> {

    protected final @Nonnull ApplicationName applicationName = ServicePortalSession.get().getLoggedInApplicationDataOrThrow().application;
    protected final @Nonnull SerializableSupplier<PublishEnvironment> environment;
    protected final @Nonnull SerializableSupplier<NodeName> endpoint;
    
    @SuppressWarnings("TryWithIdenticalCatches")
    @Override protected ArrayList<ParameterName> populate() {
        if (endpoint.get() == null) return new ArrayList<>();

        try (var tx = DeploymentParameters.get().newDbTransaction(); var ignored = new Timer(getClass().getSimpleName()+".load")) {
            var application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment.get());
            var endpointDefn = application.getEndpoints().findEndpointOrThrow(endpoint.get());
            return new ArrayList<>(endpointDefn.parametersForHash.parameters);
        }
        catch (ApplicationFactory.ApplicationNotFoundException e) {
            // e.g. application has not been published to this environment yet
            return new ArrayList<>(); 
        }
        catch (EndpointHierarchyNode.NodeNotFoundException e) {
            // e.g. change from preview to live, old endpoint still selected
            return new ArrayList<>(); 
        }
    }
}
