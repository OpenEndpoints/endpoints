package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.GitRevision;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointMenuFolder;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
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
    protected @Getter @CheckForNull EmailSendingConfigurationFactory emailConfigurationOrNull;
    protected @Getter @CheckForNull AwsS3Configuration awsS3ConfigurationOrNull;
    protected @Getter @Nonnull AwsSecretsToParameterMapping secrets;
    protected @Getter @Nonnull ServicePortalEndpointMenuFolder servicePortalEndpointMenuItems;
    
    protected Application() { } 
    
    @SneakyThrows(ConfigurationException.class)
    public static @Nonnull Application newForTesting() {
        var result = new Application();
        result.secrets = new AwsSecretsToParameterMapping(new File("doesnt-exist"));
        return result;
    }

    public static @Nonnull Application newForTesting(@Nonnull Map<String, Transformer> transformers) {
        var result = newForTesting();
        result.transformers = transformers;
        return result;
    }
}
