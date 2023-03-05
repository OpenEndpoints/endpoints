package endpoints.serviceportal.wicket.model;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.wicket.CachingFutureModel;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.ApplicationFactory;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.serviceportal.wicket.ServicePortalSession;
import lombok.RequiredArgsConstructor;
import org.danekja.java.util.function.serializable.SerializableSupplier;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.TreeSet;

@RequiredArgsConstructor
public class EndpointNamesModel extends CachingFutureModel<ArrayList<NodeName>> {

    protected final @Nonnull ApplicationName applicationName = ServicePortalSession.get().getLoggedInApplicationDataOrThrow().application();
    protected final @Nonnull SerializableSupplier<PublishEnvironment> environment;

    @Override protected @Nonnull ArrayList<NodeName> populate() {
        try (var tx = DeploymentParameters.get().newDbTransaction(); var ignored = new Timer(getClass().getSimpleName()+".load")) {
            var application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment.get());
            var names = application.getEndpoints().getEndpointForName().keySet();
            return new ArrayList<>(new TreeSet<>(names));
        }
        catch (ApplicationFactory.ApplicationNotFoundException e) {
            // e.g. application has not been published to this environment yet
            return new ArrayList<>();
        }
    }
}
