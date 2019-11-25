package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;

public class EmptyResponseConfiguration extends ResponseConfiguration {

    public EmptyResponseConfiguration() throws ConfigurationException {
        super(null);
    }

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    public EmptyResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
        assertNoOtherElements(config);
    }
}
