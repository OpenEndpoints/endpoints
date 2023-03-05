package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.GitApplicationRepository;
import endpoints.GitApplicationRepository.RepositoryCommandFailedException;
import endpoints.PublishEnvironment;
import endpoints.GitRevision;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;

/**
 * Loads and caches Applications from disk based on the directory specified in the database (last publish)
 */
public class PublishedApplicationFactory extends ApplicationFactory {

    protected record ApplicationDefn(
        @Nonnull ApplicationName name,
        @Nonnull PublishEnvironment env
    ) { }

    protected @Nonnull File applicationCheckoutContainerDir;
    protected final @Nonnull Map<ApplicationDefn, Application> cache = new HashMap<>();
    
    /**
     * Creates the factory and schedules the loading of all known applications on the passed thread pool.
     */
    @SuppressFBWarnings("SA_LOCAL_SELF_ASSIGNMENT")
    public PublishedApplicationFactory(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads, @Nonnull File applicationCheckoutContainerDir
    ) {
        this.applicationCheckoutContainerDir = applicationCheckoutContainerDir;

        //noinspection ConstantConditions - prevents non-thread-safe transaction from being accidentally used inside thread pool
        tx = tx;

        var rows = tx.jooq()
            .select(APPLICATION_PUBLISH.APPLICATION_NAME, APPLICATION_PUBLISH.ENVIRONMENT, APPLICATION_PUBLISH.REVISION)
            .from(APPLICATION_PUBLISH).fetch();
        var repos = GitApplicationRepository.fetchAll(tx);
        for (var r : rows) {
            var name = r.value1();
            var revision = r.value3();
            var repo = repos.get(name);
            var directory = getApplicationDirectory(name, revision);

            var loadAndCacheApplication = (Runnable) () -> {
                try {
                    var application = loadApplication(threads, revision, directory);

                    synchronized (PublishedApplicationFactory.this) {
                        cache.put(new ApplicationDefn(name, r.value2()), application);
                    }
                }
                catch (Exception e) {
                    LoggerFactory.getLogger(getClass()).error("Cannot load application '"+name.name+"' (will skip)", e);
                }
            };

            threads.addTaskOffPool(() -> {
                try { 
                    repo.checkoutAtomicallyIfNecessary(revision, directory);
                    threads.addTask(loadAndCacheApplication);      // Only load the application if the checkout was successful
                }
                catch (Exception e) {
                    LoggerFactory.getLogger(getClass()).error("Cannot checkout application '"+name.name+"' from Git (will skip)", e);
                }
            });
        }
    }

    protected @Nonnull GitRevision fetchPublishedRevisionFromDb(
        @Nonnull DbTransaction db, @Nonnull ApplicationName a, @Nonnull PublishEnvironment environment
    ) throws ApplicationNotFoundException {
        var result = db.jooq()
            .select(APPLICATION_PUBLISH.REVISION)
            .from(APPLICATION_PUBLISH)
            .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(a))
            .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
            .fetchOne(APPLICATION_PUBLISH.REVISION);
        if (result == null) throw new ApplicationNotFoundException(a);
        return result;
    }

    public @Nonnull File getApplicationDirectory(@Nonnull ApplicationName a, @Nonnull GitRevision r) {
        return new File(applicationCheckoutContainerDir, a.name + "-" + r.sha256Hex());
    }

    /** This fetches previously published applications, therefore they are assumed to be valid */
    @Override public synchronized Application getApplication(
        @Nonnull DbTransaction tx, @Nonnull ApplicationName name, @Nonnull PublishEnvironment environment
    ) throws ApplicationNotFoundException {
        try {
            // Determine what revision has been published and thus which we should load
            var revision = fetchPublishedRevisionFromDb(tx, name, environment);

            // Do we already have this revision loaded?
            var cachedApp = cache.get(new ApplicationDefn(name, environment));
            if (cachedApp != null && revision.equals(cachedApp.revision)) return cachedApp;

            // Checkout the application to disk if necessary (e.g. AWS instance restarted, new blank disk)
            var directory = getApplicationDirectory(name, revision);
            var repo = GitApplicationRepository.fetch(tx, name);
            repo.checkoutAtomicallyIfNecessary(revision, directory);

            // Load the application and put into our cache
            LoggerFactory.getLogger(getClass()).info("Application '" + name.name + "' has changed or was never loaded: will reload...");
            var threads = new XsltCompilationThreads();
            cachedApp = loadApplication(threads, revision, directory);
            cache.put(new ApplicationDefn(name, environment), cachedApp);
            threads.execute();

            return cachedApp;
        }
        catch (RepositoryCommandFailedException | ConfigurationException e) {
            throw new RuntimeException(prefixExceptionMessage(
                "Application which was previously successfully published seems is invalid", e), e);
        }
    }

    public @Nonnull ApplicationConfig fetchApplicationConfig(@Nonnull DbTransaction db, @Nonnull ApplicationName applicationName) {
        var appConfig = db.jooq()
            .select(APPLICATION_CONFIG.LOCKED, APPLICATION_CONFIG.DEBUG_ALLOWED)
            .from(APPLICATION_CONFIG)
            .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName))
            .fetchSingle();
        return new ApplicationConfig(appConfig.value1(), appConfig.value2());
    }

}
