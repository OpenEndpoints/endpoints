package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.*;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.destination.DocumentGenerationDestination;
import endpoints.TransformationContext;
import endpoints.datasource.DataSource;
import endpoints.datasource.TransformationFailedException;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import javax.xml.transform.TransformerException;
import java.util.Set;

public class Transformer {
    
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

    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull DocumentGenerationDestination dest
    ) throws TransformationFailedException {
        return source.scheduleExecution(context, visibleIntermediateValues, document -> {
            try { generator.transform(dest, document, true, null, null); }
            catch (DocumentTemplateInvalidException | TransformerException e) { throw new RuntimeException(e); }
        });
    }
    
    @SneakyThrows(ConfigurationException.class)
    public static @Nonnull Transformer newIdentityTransformerForTesting() {
        var defn = new DocumentOutputDefinition(new XsltParameters(DomParser.from("<empty/>")));

        var threads = new WeaklyCachedXsltTransformer.XsltCompilationThreads();
        var result = new Transformer();
        result.source = new DataSource();
        result.generator = new DocumentGenerator(threads, defn);
        threads.execute();

        return result;
    }
}
