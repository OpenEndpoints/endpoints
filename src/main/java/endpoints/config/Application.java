package endpoints.config;

import com.databasesandlife.util.EmailTransaction.EmailSendingConfiguration;
import endpoints.GitRevision;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointMenuFolder;
import lombok.Getter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Application that has been parsed from somewhere.
 *     <p>
 * Currently only loading an application from the standard directory layout via {@link ApplicationFactory} is supported,
 * but this class is deliberately designed to allow creation from other sources in the future as well.
 */
public class Application {

    /**
     * If this application was checked out from a Git, this is the revision.
     * If it came from some other source e.g. fixed application, this is null.
     */
    protected @Getter @CheckForNull GitRevision revision;
    
    protected @Getter @Nonnull Map<String, Transformer> transformers;
    protected @Getter @Nonnull EndpointHierarchyFolderNode endpoints;
    protected @Getter @Nonnull String[] secretKeys;
    protected @Getter @CheckForNull EmailSendingConfiguration emailServerOrNull;
    protected @Getter @Nonnull ServicePortalEndpointMenuFolder servicePortalEndpointMenuItems;
    
    Application() { } 
    
    public static @Nonnull Application newForTesting(@Nonnull Map<String, Transformer> transformers) {
        var result = new Application();
        result.transformers = transformers;
        return result;
    }
    
    public static @Nonnull Application newForTesting() {
        return newForTesting(Map.of());
    }
}
