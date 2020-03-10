package endpoints;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.CannotConnectToDatabaseException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.ApplicationFactory;
import endpoints.config.ApplicationName;
import endpoints.config.FixedPathApplicationFactory;
import endpoints.config.PublishedApplicationFactory;
import org.apache.log4j.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;
import java.util.Optional;

import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;

/**
 * Represents those parameters which can change from one deployment to another. 
 * For example, JDBC database URLs are different on the "pre-production" server to the "live server".
 *    <p>
 * These parameters are taken from various environment variables, see "endpoints.lyx" for more details.
 *    <p>
 * This class not only represents a set of these parameters, but also has methods which make sense given those parameters,
 * such as creating a new database connection with the JDBC URL, or "self-testing" e.g. that the database connection
 * specifies a valid database.
 */
public class DeploymentParameters {
    
    private static DeploymentParameters sharedInstance = null;

    public final @Nonnull String jdbcUrl;
    public final @CheckForNull File publishedApplicationsDirectory;
    public final boolean checkHash, displayExpectedHash, xsltDebugLog;
    public final @CheckForNull String gitRepositoryDefaultPattern;
    public final @CheckForNull String servicePortalEnvironmentDisplayName;
    
    protected @CheckForNull ApplicationFactory applications = null;
    
    public static synchronized DeploymentParameters get() {
        if (sharedInstance == null) sharedInstance = new DeploymentParameters();
        return sharedInstance;
    }

    /** @return never return empty string */
    protected Optional<String> getOptionalParameter(@Nonnull String var) {
        var result = System.getenv(var);
        if (result == null) return Optional.empty();
        if (result.isEmpty()) return Optional.empty();  // It is useful to "-e FOO=" to disable environment variables
        return Optional.of(result);
    }

    protected @Nonnull String getMandatoryParameter(@Nonnull String var) {
        var result = getOptionalParameter(var);
        if ( ! result.isPresent()) throw new RuntimeException("Environment variable '" + var + "' is not set");
        return result.get();
    }
    
    protected boolean isFixedApplicationMode() {
        return gitRepositoryDefaultPattern == null;
    }

    protected DeploymentParameters() {
        jdbcUrl = getMandatoryParameter("ENDPOINTS_JDBC_URL");
        publishedApplicationsDirectory = 
            getOptionalParameter("ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY").map(s -> new File(s)).orElse(null);
        checkHash =
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_CHECK_HASH").orElse("true"));
        displayExpectedHash = 
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_DISPLAY_EXPECTED_HASH").orElse("false"));
        xsltDebugLog = 
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_XSLT_DEBUG_LOG").orElse("false"));
        gitRepositoryDefaultPattern = 
            getOptionalParameter("ENDPOINTS_GIT_REPOSITORY_DEFAULT_PATTERN").orElse(null);
        servicePortalEnvironmentDisplayName =
            getOptionalParameter("ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME").orElse(null);

        Logger.getLogger(getClass()).info("Endpoints server application is in " + 
            (isFixedApplicationMode() 
                ? "SINGLE APPLICATION mode" 
                : "MULTIPLE APPLICATIONS mode (via service portal, publishing from Git)"));
    }
    
    public synchronized ApplicationFactory getApplications(@Nonnull DbTransaction tx) {
        if (applications == null) {
            var threads = new XsltCompilationThreads();
            if (isFixedApplicationMode()) {
                applications = new FixedPathApplicationFactory(tx, threads);
            }
            else {
                assert publishedApplicationsDirectory != null;
                applications = new PublishedApplicationFactory(tx, threads, publishedApplicationsDirectory);
            }
            threads.execute();
        }
        return applications;
    }

    public @Nonnull GitApplicationRepository getGitRepository(@Nonnull ApplicationName application)
    throws ConfigurationException {
        var patternVars = new HashMap<String, String>() {{ put("applicationName", application.name); }};

        // Is an override set?
        var mangledName = application.name
            .replaceAll("[^\\w]+", "_")  // replace all hyphens etc. with a single underscore
            .replaceAll("^_?(.*?)_?$", "$1")  // replace leading/trailing underscores
            .toUpperCase();
        var overrideEnvVarName = "ENDPOINTS_GIT_REPOSITORY_OVERRIDE_" + mangledName;
        var candidateGitRepo = getOptionalParameter(overrideEnvVarName);
        if (candidateGitRepo.isPresent())
            return new GitApplicationRepository(replacePlainTextParameters(candidateGitRepo.get(), patternVars));

        // If not, use default pattern
        if (gitRepositoryDefaultPattern == null) throw new ConfigurationException("Not configured to publish from Git repos");
        return new GitApplicationRepository(replacePlainTextParameters(gitRepositoryDefaultPattern, patternVars));
    }

    public DbTransaction newDbTransaction() throws CannotConnectToDatabaseException {
        return new DbTransaction(jdbcUrl);
    }
}
