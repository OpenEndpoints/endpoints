package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.DocumentGenerationDestination;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.DocumentOutputDefinition;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.TransformationContext;
import endpoints.datasource.DataSource;
import endpoints.datasource.TransformationFailedException;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class Transformer {
    
    protected @Nonnull DataSource source;
    protected @Nonnull @Getter DocumentOutputDefinition defn;
    protected @Nonnull DocumentGenerator generator;

    /** Check that no parameters, other than the ones passed to this method, are necessary to perform the transformation */
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        source.assertParametersSuffice(params);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        generator.assertTemplateValid();
    }

    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context, @Nonnull DocumentGenerationDestination dest
    ) throws TransformationFailedException {
        return source.scheduleExecution(context, document -> {
            try { generator.transform(dest, document, true, null); }
            catch (DocumentTemplateInvalidException e) { throw new RuntimeException(e); }
        });
    }
    
    @SneakyThrows(ConfigurationException.class)
    public static @Nonnull Transformer newIdentityTransformerForTesting() {
        var defn = new DocumentOutputDefinition(Collections.emptyMap());

        var threads = new WeaklyCachedXsltTransformer.XsltCompilationThreads();
        var result = new Transformer();
        result.source = new DataSource();
        result.generator = new DocumentGenerator(threads, defn);
        threads.execute();

        return result;
    }


}
