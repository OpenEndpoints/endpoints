package endpoints.config.response;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;

public class EmptyResponseConfiguration extends ResponseConfiguration {

    public EmptyResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
        assertNoOtherElements(config);
    }

    @SneakyThrows(ConfigurationException.class)
    public static EmptyResponseConfiguration newForTesting() {
        return new EmptyResponseConfiguration(DomParser.from("<unit-test/>"));
    }
}
