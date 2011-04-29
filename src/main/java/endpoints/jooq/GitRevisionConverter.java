package endpoints.jooq;

import endpoints.GitRevision;
import org.jooq.Converter;

import javax.annotation.CheckForNull;

@SuppressWarnings("serial")
public class GitRevisionConverter implements Converter<String, GitRevision> {

    @Override public Class<String> fromType() { return String.class; }
    @Override public Class<GitRevision> toType() { return GitRevision.class; }

    @Override
    public @CheckForNull GitRevision from(@CheckForNull String x) {
        if (x == null) return null;
        return new GitRevision(x);
    }

    @Override
    public @CheckForNull String to(@CheckForNull GitRevision x) {
        if (x == null) return null;
        return x.getSha256Hex();
    }
}
