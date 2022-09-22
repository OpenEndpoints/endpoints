package endpoints.config.response;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.destination.BufferedHttpResponseDocumentGenerationDestination;
import endpoints.OoxmlParameterExpander;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;

public class OoxmlParameterExpansionResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull OoxmlParameterExpander expander;

    public OoxmlParameterExpansionResponseConfiguration(
        @Nonnull File ooxmlContainerDir, @Nonnull Element config, @Nonnull Element responseElement
    ) throws ConfigurationException {
        super(config);

        assertNoOtherElements(config, "ooxml-parameter-expansion");
        
        this.expander = new OoxmlParameterExpander(ooxmlContainerDir, "download-filename", responseElement);
    }

    @Override public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        expander.assertParametersSuffice(params, inputIntermediateValues);
    }

    @Override public boolean isDownload() { return true; }
    
    public void scheduleExecution(
        @Nonnull TransformationContext context, 
        @Nonnull BufferedHttpResponseDocumentGenerationDestination destination
    ) {
        expander.scheduleExecution(context, destination, inputIntermediateValues);
    }
}
