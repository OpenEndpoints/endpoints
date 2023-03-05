package endpoints;

import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;

public record GitRevision(
    @Nonnull String sha256Hex
) {
    public String getAbbreviated() {
        return StringUtils.substring(sha256Hex, 0, 8);
    }
}
