package endpoints.config;


import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class UrlPrefixWhiteList {

    public final @Nonnull List<String> urlPrefixWhiteList = new ArrayList<>();

    public boolean isUrlInWhiteList(@Nonnull String url) {
        // No prefixes defined in config? Means we accept everything
        if (urlPrefixWhiteList.isEmpty()) 
            return true;
        
        for (var prefix : urlPrefixWhiteList)
            if (url.startsWith(prefix)) 
                return true;
        
        return false;
    }
}
