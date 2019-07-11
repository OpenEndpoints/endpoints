package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;

public class EmptyResponseConfiguration extends ResponseConfiguration {

    public EmptyResponseConfiguration() throws ConfigurationException {
        super(null);
    }
}
