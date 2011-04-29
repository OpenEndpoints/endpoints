package endpoints;

import lombok.Value;
import org.apache.commons.lang.StringUtils;

@Value
public class GitRevision {
    protected String sha256Hex;

    public String getAbbreviated() {
        return StringUtils.substring(sha256Hex, 0, 8);
    }
}
