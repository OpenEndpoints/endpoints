package endpoints.config;

import com.databasesandlife.util.EmailTransaction.*;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static endpoints.PlaintextParameterReplacer.containsParameters;

public class EmailSendingConfigurationFactory {

    protected final @CheckForNull String mxAddressElementPattern, usernamePattern, passwordPattern, serverPattern, portPattern;
    
    /** 
     * Extra headers that should be added to emails sent.
     * Keys are not patterns, values are patterns. 
     */
    protected final @Nonnull Map<String, String> extraHeaderPatternForHeaderKey; 

    public EmailSendingConfigurationFactory(@Nonnull File file) throws ConfigurationException {
        try {
            var rootEl = from(file);
    
            if ( ! rootEl.getNodeName().equals("email-sending-configuration"))
                throw new ConfigurationException("Root node must be <email-sending-configuration>");
            assertNoOtherElements(rootEl, "mx-address", "server", "username", "password", "port", "header");
    
            mxAddressElementPattern = getOptionalSingleSubElementTextContent(rootEl, "mx-address");
            usernamePattern = getOptionalSingleSubElementTextContent(rootEl, "username");
            passwordPattern = getOptionalSingleSubElementTextContent(rootEl, "password");
            serverPattern = getOptionalSingleSubElementTextContent(rootEl, "server");
            portPattern = getOptionalSingleSubElementTextContent(rootEl, "port");
            
            if (portPattern != null && ! containsParameters(portPattern)) {
                try { Integer.parseInt(portPattern); }
                catch (NumberFormatException e) { throw new ConfigurationException("<port>", e); }
            }
            
            extraHeaderPatternForHeaderKey = new HashMap<>();
            for (var e : getSubElements(rootEl, "header")) {
                String key = getMandatoryAttribute(e, "name");
                String value = e.getTextContent().trim();
                extraHeaderPatternForHeaderKey.put(key, value);
            }
        }
        catch (ConfigurationException e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            mxAddressElementPattern, "'mx-address' element");
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            usernamePattern, "'username' element");
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            passwordPattern, "'password' element");
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            serverPattern, "'server' element");
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            portPattern, "'port' element");
        for (var e : extraHeaderPatternForHeaderKey.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
                e.getValue(), "<header name='"+e.getKey()+"'> element");
    }

    public @Nonnull EmailSendingConfiguration generate(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        var stringParams = context.getStringParametersIncludingIntermediateValues(visibleIntermediateValues);
        
        final SmtpServerConfiguration smtp;
        if (mxAddressElementPattern != null) {
            var mx = new MxSmtpConfiguration();
            mx.mxAddress = replacePlainTextParameters(mxAddressElementPattern, stringParams);
            smtp = mx;
        } else {
            SmtpServerAddress address;

            if (usernamePattern != null) {
                var tls = new TlsSmtpServerAddress();
                tls.username = replacePlainTextParameters(usernamePattern, stringParams);
                tls.password = replacePlainTextParameters(passwordPattern, stringParams);
                address = tls;
            } else {
                address = new SmtpServerAddress();
            }

            address.host = replacePlainTextParameters(serverPattern, stringParams);

            if (portPattern != null) {
                var port = replacePlainTextParameters(portPattern, stringParams);
                try { address.port = Integer.parseInt(port); }
                catch (NumberFormatException e) { throw new ConfigurationException("<port>", e); }
            }

            smtp = address;
        }

        var result = new EmailSendingConfiguration(smtp);

        for (var e : extraHeaderPatternForHeaderKey.entrySet()) 
            result.extraHeaders.put(e.getKey(), replacePlainTextParameters(e.getValue(), stringParams));

        return result;
    }
}
