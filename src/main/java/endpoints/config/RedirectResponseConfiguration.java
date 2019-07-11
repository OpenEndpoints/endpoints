package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PlaintextParameterReplacer;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Set;

public class RedirectResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull String urlPattern;
    public @Nonnull UrlPrefixWhiteList whitelist;

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    public RedirectResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
    }

    @Override
    public void assertParametersSuffice(Set<ParameterName> params) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, urlPattern, "<redirect-to>");
    }
}
