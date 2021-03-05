package endpoints.config.response;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.ParameterName;
import endpoints.config.UrlPrefixWhiteList;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getSubElements;

public class RedirectResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull String urlPattern;
    public @Nonnull UrlPrefixWhiteList whitelist;

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    public RedirectResponseConfiguration(@Nonnull Element config, @Nonnull Element redirectToElement) throws ConfigurationException {
        super(config);
        
        assertNoOtherElements(config, "redirect-to", "redirect-prefix-whitelist-entry", "input-intermediate-value");
        urlPattern = redirectToElement.getTextContent().trim();
        whitelist = new UrlPrefixWhiteList();
        for (var e : getSubElements(config, "redirect-prefix-whitelist-entry"))
            whitelist.urlPrefixWhiteList.add(e.getTextContent().trim());
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, urlPattern, "<redirect-to>");
    }
    
    @SneakyThrows(ConfigurationException.class)
    public static @Nonnull RedirectResponseConfiguration newForTesting(@Nonnull String condition, @Nonnull String destinationUrl) {
        return new RedirectResponseConfiguration(
            DomParser.from("<foo "+condition+">" +
                "<redirect-prefix-whitelist-entry>"+destinationUrl+"</redirect-prefix-whitelist-entry></foo>"),
            DomParser.from("<foo>"+destinationUrl+"</foo>")
        );
    }
}
