package endpoints;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public enum PublishEnvironment {

    live, preview;

    public static class PublishEnvironmentNotFoundException extends Exception { }

    public static @Nonnull PublishEnvironment getDefault() {
        return live;
    }

    public static @Nonnull PublishEnvironment parseOrDefault(@CheckForNull String environment)
    throws PublishEnvironmentNotFoundException {
        if (environment == null) {
            return getDefault();
        }
        else {
            try { return valueOf(environment); }
            catch (IllegalArgumentException e) { throw new PublishEnvironmentNotFoundException(); }
        }
    }
}
