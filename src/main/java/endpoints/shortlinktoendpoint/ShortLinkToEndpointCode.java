package endpoints.shortlinktoendpoint;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import lombok.Value;

import javax.annotation.Nonnull;

@Value
public class ShortLinkToEndpointCode {
    
    String code; 
    
    public static @Nonnull ShortLinkToEndpointCode newRandom() {
        return new ShortLinkToEndpointCode(CleartextPassword.newRandom(12).getCleartext());
    }
}
