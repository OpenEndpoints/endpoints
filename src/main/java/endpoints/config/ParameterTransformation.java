package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.datasource.DataSourceCommand;

import javax.annotation.Nonnull;
import java.util.List;

public class ParameterTransformation {

    public @Nonnull List<DataSourceCommand> dataSourceCommands;
    public @Nonnull WeaklyCachedXsltTransformer xslt;

    public void assertTemplatesValid() throws WeaklyCachedXsltTransformer.DocumentTemplateInvalidException {
        xslt.assertValid();
    }
}
