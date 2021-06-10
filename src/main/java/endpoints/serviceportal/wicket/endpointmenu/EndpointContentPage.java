package endpoints.serviceportal.wicket.endpointmenu;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointContentMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;

import javax.annotation.Nonnull;

public class EndpointContentPage extends EndpointPage {
    
    public EndpointContentPage(@Nonnull PublishEnvironment environment, @Nonnull MultiEnvironmentEndpointLeafMenuItem item) {
        super(environment, item);
        
        try (DbTransaction tx = DeploymentParameters.get().newDbTransaction()) {
            var i = (ServicePortalEndpointContentMenuItem) item.itemForEnvironment.get(environment);
            add(new EndpointPanel("endpointContent", environment, i.content).execute(tx));
        }
    }
}
