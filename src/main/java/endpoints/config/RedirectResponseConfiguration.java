package endpoints.config;

import javax.annotation.Nonnull;

public class RedirectResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull String url;
    public @Nonnull UrlPrefixWhiteList whitelist;

}
