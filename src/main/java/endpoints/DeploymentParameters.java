package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.CannotConnectToDatabaseException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.ApplicationFactory;
import endpoints.config.FixedPathApplicationFactory;
import endpoints.config.PublishedApplicationFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.ZoneId;
import java.util.Optional;

import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;

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
@Slf4j
public class DeploymentParameters {
    
    private static DeploymentParameters sharedInstance = null;

    /** Has trailing slash */ public final @Nonnull URL baseUrl;
    public final @Nonnull String jdbcUrl;
    public final @CheckForNull URI awsS3EndpointOverride, awsSecretsManagerEndpointOverride;
    public final @Nonnull File publishedApplicationsDirectory;
    public final boolean checkHash, displayExpectedHash, xsltDebugLog;
    public final @CheckForNull String servicePortalEnvironmentDisplayName;
    public final @CheckForNull ZoneId singleApplicationModeTimezoneId;
    
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
        if (result.isEmpty()) throw new RuntimeException("Environment variable '" + var + "' is not set");
        return result.get();
    }
    
    public boolean isSingleApplicationMode() {
        return FixedPathApplicationFactory.isFixedPathPresent();
    }

    @SneakyThrows(MalformedURLException.class)
    protected DeploymentParameters() {
        baseUrl = new URL(getMandatoryParameter("ENDPOINTS_BASE_URL"));
        jdbcUrl = getMandatoryParameter("ENDPOINTS_JDBC_URL");
        awsS3EndpointOverride = getOptionalParameter("ENDPOINTS_AWS_S3_ENDPOINT_OVERRIDE")
            .map(x -> URI.create(x)).orElse(null);
        awsSecretsManagerEndpointOverride = getOptionalParameter("ENDPOINTS_AWS_SECRETS_MANAGER_ENDPOINT_OVERRIDE")
            .map(x -> URI.create(x)).orElse(null);
        publishedApplicationsDirectory = 
            getOptionalParameter("ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY").map(s -> new File(s)).orElse(new File("/tmp"));
        checkHash =
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_CHECK_HASH").orElse("true"));
        displayExpectedHash = 
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_DISPLAY_EXPECTED_HASH").orElse("false"));
        xsltDebugLog = 
            Boolean.parseBoolean(getOptionalParameter("ENDPOINTS_XSLT_DEBUG_LOG").orElse("false"));
        servicePortalEnvironmentDisplayName =
            getOptionalParameter("ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME").orElse(null);
        singleApplicationModeTimezoneId = 
            getOptionalParameter("ENDPOINTS_SINGLE_APPLICATION_MODE_TIMEZONE_ID").map(s -> ZoneId.of(s)).orElse(null);

        log.info("Endpoints server application is in " + 
            (isSingleApplicationMode() 
                ? "SINGLE APPLICATION mode" 
                : "MULTIPLE APPLICATIONS mode (via service portal, publishing from Git)"));
    }
    
    public synchronized ApplicationFactory getApplications(@Nonnull DbTransaction tx) {
        if (applications == null) {
            var threads = new XsltCompilationThreads();
            applications = isSingleApplicationMode() 
                ? new FixedPathApplicationFactory(threads) 
                : new PublishedApplicationFactory(tx, threads, publishedApplicationsDirectory);
            threads.execute();
        }
        return applications;
    }

    public DbTransaction newDbTransaction() throws CannotConnectToDatabaseException {
        return new DbTransaction(jdbcUrl);
    }
    
    protected void setAwsEndpointOverride(
        @Nonnull AwsClientBuilder<?, ?> builder,
        @CheckForNull URI endpointOverride
    ) {
        if (endpointOverride == null) return;
        
        // If endpointOverride is set, assume we're using "localstack" which ignores credentials,
        //     but the AWS client library still throws an error if they're not set so we must set some values here.
        // Otherwise, assume we're using real AWS, so take credentials from the usual places.
        //     i.e. environment variables or ECS containers.
        builder.credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("ignored-by-localstack", "ignored-by-localstack")));
        builder.endpointOverride(endpointOverride);
    }

    public @Nonnull S3Client newAwsS3Client() {
        var builder = S3Client.builder();
        if (awsS3EndpointOverride != null) builder.region(AWS_GLOBAL);  // Needed for localstack
        setAwsEndpointOverride(builder, awsS3EndpointOverride);
        return builder.build();
    }

    public @Nonnull SecretsManagerClient newAwsSecretsManagerClient(@Nonnull Region region) {
        var builder = SecretsManagerClient.builder();
        builder.region(region);
        setAwsEndpointOverride(builder, awsSecretsManagerEndpointOverride);
        return builder.build();
    }
}
