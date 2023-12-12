package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.DocumentOutputDefinition;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.XsltParameters;
import com.offerready.xslt.destination.BufferedDocumentGenerationDestination;
import endpoints.TransformationContext;
import endpoints.datasource.DataSource;
import endpoints.datasource.TransformationFailedException;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.transform.TransformerException;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Transformer {
    
    protected @CheckForNull WriteTransformationDataToAwsS3Command writeInputToAwsS3, writeOutputToAwsS3;
    protected @Nonnull String sourceName;
    protected @Nonnull DataSource source;
    protected @Nonnull @Getter DocumentOutputDefinition defn;
    protected @Nonnull DocumentGenerator generator;

    /** Check that no parameters, other than the ones passed to this method, are necessary to perform the transformation */
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        source.assertParametersSuffice(params, visibleIntermediateValues);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        generator.assertTemplateValid();
    }

    public boolean requiresAwsS3Configuration() {
        return writeInputToAwsS3 != null || writeOutputToAwsS3 != null;
    }
    
    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull BufferedDocumentGenerationDestination dest
    ) throws TransformationFailedException {
        return source.scheduleExecution(context, visibleIntermediateValues, document -> {
            try { 
                if (writeInputToAwsS3 != null) {
                    assert context.application.awsS3ConfigurationOrNull != null : "checked in requiresAwsS3Configuration";
                    writeInputToAwsS3.scheduleWriting(context.threads, context.environment, context.requestId,
                        context.application.awsS3ConfigurationOrNull, sourceName,
                        "application/xml", DomParser.formatXmlPretty(document.getDocumentElement()).getBytes(UTF_8));
                }
                
                generator.transform(dest, document, true, null, null);
                
                if (writeOutputToAwsS3 != null) {
                    assert context.application.awsS3ConfigurationOrNull != null : "checked in requiresAwsS3Configuration";
                    writeOutputToAwsS3.scheduleWriting(context.threads, context.environment, context.requestId,
                        context.application.awsS3ConfigurationOrNull, sourceName,
                        dest.getContentType(), dest.getBody().toByteArray());
                }
            }
            catch (DocumentTemplateInvalidException | TransformerException e) { throw new RuntimeException(e); }
        });
    }
    
    @SneakyThrows(ConfigurationException.class)
    public static @Nonnull Transformer newIdentityTransformerForTesting() {
        var defn = new DocumentOutputDefinition(new XsltParameters(DomParser.from("<empty/>")));

        var threads = new WeaklyCachedXsltTransformer.XsltCompilationThreads();
        var result = new Transformer();
        result.source = DataSource.newEmptyForTesting();
        result.generator = new DocumentGenerator(threads, defn);
        threads.execute();

        return result;
    }
}
