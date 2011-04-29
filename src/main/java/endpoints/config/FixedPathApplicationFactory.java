package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PublishEnvironment;

import javax.annotation.Nonnull;
import java.io.File;

/** Provides only one application called "endpoints" which is at "/var/endpoints/fixed-application" in the filesystem */
public class FixedPathApplicationFactory extends ApplicationFactory {

    final @Nonnull Application application;

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public FixedPathApplicationFactory(@Nonnull DbTransaction db, @Nonnull XsltCompilationThreads threads) {
        try {
            application = loadApplication(threads, db, new File("/var/endpoints/fixed-application"));
        }
        catch (ConfigurationException e) {
            throw new RuntimeException("Application at fixed location is invalid: " + e.getMessage(), e);
        }
    }

    @Override public synchronized @Nonnull Application getApplication(
        @Nonnull DbTransaction db, @Nonnull ApplicationName name, @Nonnull PublishEnvironment environment
    ) throws ApplicationNotFoundException {
        if ( ! environment.equals(PublishEnvironment.getDefault())) throw new ApplicationNotFoundException(name);
        if ( ! "endpoints".equals(name.name)) throw new ApplicationNotFoundException(name);
        return application;
    }
}
