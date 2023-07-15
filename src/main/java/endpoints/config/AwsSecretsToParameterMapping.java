package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.LazyCachingValue;
import endpoints.LazyCachingValue.LazyParameterComputationException;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static software.amazon.awssdk.regions.Region.US_EAST_1;

public class AwsSecretsToParameterMapping {
    
    public final @Nonnull Region region;
    public final @Nonnull Map<String, String> secretNameForParameterName;
    
    public AwsSecretsToParameterMapping(@Nonnull File file) throws ConfigurationException {
        if ( ! file.exists()) {
            region = US_EAST_1; // never used, as secrets are never looked up
            secretNameForParameterName = Map.of();
            return;
        }
        
        try {
            var container = from(file);
            if ( ! container.getNodeName().equals("aws-secrets"))
                throw new ConfigurationException("Root node must be <aws-secrets>");
            region = Region.of(getMandatoryAttribute(container, "region"));

            assertNoOtherElements(container, "secret");
            var mapping = new HashMap<String, String>();
            for (var e : getSubElements(container, "secret"))
                mapping.put(getMandatoryAttribute(e, "parameter-name"), getMandatoryAttribute(e, "aws-secret"));
            secretNameForParameterName = Collections.unmodifiableMap(mapping);
        }
        catch (ConfigurationException e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }
    
    protected @Nonnull String fetchSecret(@Nonnull String secretName) throws LazyParameterComputationException {
        try (var client = DeploymentParameters.get().newAwsSecretsManagerClient(region)) {
            return client.getSecretValue(r -> r.secretId(secretName)).secretString();
        }
        catch (Exception e) { throw new LazyParameterComputationException("Cannot fetch AWS secret '" + secretName + "'", e); }
    }
    
    public @Nonnull Map<String, LazyCachingValue> getValues() {
        return secretNameForParameterName.entrySet().stream().collect(Collectors.toMap(
            e -> e.getKey(),
            e -> new LazyCachingValue() {
                @Override protected @Nonnull String computeParameter() throws LazyParameterComputationException {
                    try { return fetchSecret(e.getValue()); }
                    catch (LazyParameterComputationException x) {
                        throw new LazyParameterComputationException("Cannot compute parameter '" + e.getKey() + "'", x); 
                    } 
                }
            }
        ));
    }
}
