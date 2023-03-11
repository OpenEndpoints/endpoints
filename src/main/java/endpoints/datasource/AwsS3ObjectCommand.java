package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.DeploymentParameters;
import endpoints.TransformationContext;
import endpoints.config.AwsS3Configuration;
import endpoints.config.IntermediateValueName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getMandatoryAttribute;

public class AwsS3ObjectCommand extends DataSourceCommand {
    
    protected @Nonnull String key;
    
    public AwsS3ObjectCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, config);
        key = getMandatoryAttribute(config, "key");
    }

    @Override
    public boolean requiresAwsS3Configuration() { 
        return true; 
    }
    
    protected @Nonnull Element execute(@Nonnull AwsS3Configuration s3) throws TransformationFailedException {
        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            var response = client.getObject(r -> r
                .bucket(s3.bucket())
                .key(key));
            var bytes = response.readAllBytes();
            var xml = DomParser.from(new ByteArrayInputStream(bytes));
            
            var result = DomParser.newDocumentBuilder().newDocument();
            var root = result.createElement("aws-s3-object");
            root.setAttribute("key", key);
            root.appendChild(result.importNode(xml, true));
            return root;
        }
        catch (IOException | ConfigurationException e) { throw new TransformationFailedException(e); }
    }
  
    @Override public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var result = new DataSourceCommandFetcher() {
            @Override protected Element[] populateOrThrow() throws TransformationFailedException {
                AwsS3Configuration s3 = context.application.getAwsS3ConfigurationOrNull();
                assert s3 != null; // checked on application load
                return new Element[] { execute(s3) };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
