package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.EmailTransaction.*;
import com.databasesandlife.util.gwtsafe.ConfigurationException;

import javax.annotation.Nonnull;
import java.io.File;

public class EmailSendingConfigurationParser extends DomParser {

    public static @Nonnull EmailSendingConfiguration parse(@Nonnull File file) throws ConfigurationException {
        try {
            var rootEl = from(file);
    
            if ( ! rootEl.getNodeName().equals("email-sending-configuration"))
                throw new ConfigurationException("Root node must be <email-sending-configuration>");
            assertNoOtherElements(rootEl, "mx-address", "server", "username", "password", "port", "header");
    
            final SmtpServerConfiguration smtp;
            var mxAddressElement = getOptionalSingleSubElement(rootEl, "mx-address");
            if (mxAddressElement != null) {
                var mx = new MxSmtpConfiguration();
                mx.mxAddress = mxAddressElement.getTextContent();
                smtp = mx;
            } else {
                SmtpServerAddress address;
    
                if (getOptionalSingleSubElement(rootEl, "username") != null) {
                    var tls = new TlsSmtpServerAddress();
                    tls.username = getMandatorySingleSubElement(rootEl, "username").getTextContent();
                    tls.password = getMandatorySingleSubElement(rootEl, "password").getTextContent();
                    address = tls;
                }
                else {
                    address = new SmtpServerAddress();
                }
    
                address.host = getMandatorySingleSubElement(rootEl, "server").getTextContent();
    
                var port = getOptionalSingleSubElement(rootEl, "port");
                if (port != null) try { address.port = Integer.parseInt(port.getTextContent()); }
                catch (NumberFormatException e) { throw new ConfigurationException("<port>", e); }
    
                smtp = address;
            }
    
            var result = new EmailSendingConfiguration(smtp);
    
            for (var e : getSubElements(rootEl, "header")) {
                String key = getMandatoryAttribute(e, "name");
                String value = e.getTextContent().trim();
                result.extraHeaders.put(key, value);
            }
            
            return result;
        }
        catch (Exception e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }
}
