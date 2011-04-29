package endpoints;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.CannotConnectToDatabaseException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.ApplicationFactory;
import endpoints.config.ApplicationName;
import endpoints.config.FixedPathApplicationFactory;
import endpoints.config.PublishedApplicationFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;

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
    public final @Nonnull File publishedApplicationsDirectory;
    public final boolean checkHash, displayExpectedHash, logParameterTransformation;
    public final @CheckForNull String gitRepositoryDefaultPattern;
    public final @CheckForNull String servicePortalEnvironmentDisplayName;
    
    protected @CheckForNull ApplicationFactory applications = null;
    
    public static synchronized DeploymentParameters get() {
        if (sharedInstance == null) sharedInstance = new DeploymentParameters();
        return sharedInstance;
    }

    protected String getOptionalParameterOrNull(@Nonnull String var) {
        return System.getenv(var);
    }

    protected File getOptionalFileParameterOrNull(@Nonnull String var) {
        var result = getOptionalParameterOrNull(var);
        if (result == null) return null;
        else return new File(result);
    }

    protected String getOptionalParameter(@Nonnull String var, @Nonnull String defaultValue) {
        var result = getOptionalParameterOrNull(var);
        if (result == null) return defaultValue;
        else return result;
    }

    protected String getMandatoryParameter(@Nonnull String var) {
        var result = getOptionalParameterOrNull(var);
        if (result == null) throw new RuntimeException("Environment variable '" + var + "' is not set");
        return result;
    }

    protected DeploymentParameters() {
        jdbcUrl = getMandatoryParameter("ENDPOINTS_JDBC_URL");
        publishedApplicationsDirectory = getOptionalFileParameterOrNull("ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY");
        checkHash = Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_CHECK_HASH", "true"));
        displayExpectedHash = Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_DISPLAY_EXPECTED_HASH", "false"));
        logParameterTransformation = Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_LOG_PARAMETER_TRANSFORMATION", "false"));
        gitRepositoryDefaultPattern = getOptionalParameterOrNull("ENDPOINTS_GIT_REPOSITORY_DEFAULT_PATTERN");
        servicePortalEnvironmentDisplayName = getOptionalParameterOrNull("ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME");
    }
    
    public synchronized ApplicationFactory getApplications(@Nonnull DbTransaction tx) {
        if (applications == null) {
            var threads = new XsltCompilationThreads();
            if (gitRepositoryDefaultPattern == null) applications = new FixedPathApplicationFactory(tx, threads);
            else applications = new PublishedApplicationFactory(tx, threads, publishedApplicationsDirectory);
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
        var candidateGitRepo = getOptionalParameterOrNull(overrideEnvVarName);
        if (candidateGitRepo != null) return new GitApplicationRepository(replacePlainTextParameters(candidateGitRepo, patternVars));

        // If not, use default pattern
        if (gitRepositoryDefaultPattern == null) throw new ConfigurationException("Not configured to publish from Git repos");
        return new GitApplicationRepository(replacePlainTextParameters(gitRepositoryDefaultPattern, patternVars));
    }

    public DbTransaction newDbTransaction() throws CannotConnectToDatabaseException {
        return new DbTransaction(jdbcUrl);
    }
}
