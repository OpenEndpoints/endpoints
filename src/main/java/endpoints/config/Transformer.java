package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.DocumentGenerationDestination;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.DocumentOutputDefinition;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.TransformationContext;
import endpoints.datasource.DataSource;
import endpoints.datasource.TransformationFailedException;

import javax.annotation.Nonnull;
import java.util.Collection;

public class Transformer {
    
    protected @Nonnull DataSource source;
    protected @Nonnull DocumentOutputDefinition defn;
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
}
