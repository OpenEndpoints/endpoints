package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.DocumentGenerationDestination;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.DocumentOutputDefinition;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.datasource.DataSource;
import endpoints.datasource.TransformationFailedException;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    public void execute(
        @Nonnull ApplicationTransaction tx, @Nonnull DocumentGenerationDestination dest,
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        boolean transform
    ) throws DocumentTemplateInvalidException, TransformationFailedException {
        var dataSourceResult = source.execute(tx, params, fileUploads, autoInc);
        generator.transform(dest, dataSourceResult, transform, null);
    }
}
