package endpoints;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.GitApplicationRepository.RepositoryCommandFailedException;
import endpoints.config.ApplicationFactory;
import endpoints.config.ApplicationName;
import endpoints.config.PublishedApplicationFactory;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;

@RequiredArgsConstructor
public class PublishProcess {

    @Nonnull ApplicationName applicationName;
    @Nonnull PublishEnvironment environment;

    @FunctionalInterface
    public interface PublishLogger {
        public void println(String line);
    }

    public static class ApplicationInvalidException extends Exception {
        ApplicationInvalidException(String msg, Throwable t) { super(msg, t); }
    }

    public static void setApplicationToPublished(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment,
        @Nonnull GitRevision revision
    ) {
        tx.jooq()
            .insertInto(APPLICATION_PUBLISH)
            .set(APPLICATION_PUBLISH.APPLICATION_NAME, applicationName)
            .set(APPLICATION_PUBLISH.ENVIRONMENT, environment)
            .set(APPLICATION_PUBLISH.REVISION, revision)
            .onDuplicateKeyUpdate()
            .set(APPLICATION_PUBLISH.REVISION, revision)
            .execute();
    }

    public @Nonnull GitRevision publish(@Nonnull DbTransaction tx, @Nonnull PublishLogger log) throws ApplicationInvalidException {
        File directory = null;
        try (var ignored = new Timer("publish '"+applicationName.name+"'")) {
            // Otherwise acquiring lock of application leads to: ERROR: could not serialize access due to concurrent update
            tx.execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED");

            var repo = GitApplicationRepository.fetch(tx, applicationName);

            log.println(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z").format(Instant.now().atZone(ZoneId.systemDefault())));

            String envLog = environment != PublishEnvironment.getDefault() ? " to "+environment.name()+" environment" : "";
            log.println("Starting publish of application '"+applicationName.name+"'"+envLog+"...");
            var applications = (PublishedApplicationFactory) DeploymentParameters.get().getApplications(tx);

            log.println("Determining latest revision in repository...");
            var revision = repo.fetchLatestRevision();
            directory = applications.getApplicationDirectory(applicationName, revision);

            log.println("Acquire exclusive lock to publish the application...");
            setApplicationToPublished(tx, applicationName, environment, revision);

            log.println("Checkout out revision " + revision.getAbbreviated() + " from repository...");
            repo.checkoutAtomicallyIfNecessary(revision, directory);

            log.println("Verifying application...");
            var threads = new XsltCompilationThreads();
            var application = ApplicationFactory.loadApplication(threads, revision, directory);
            threads.execute();
            application.getEndpoints().assertTemplatesValid();

            return revision;
        }
        catch (RepositoryCommandFailedException | ConfigurationException | DocumentTemplateInvalidException e) {
            throw new ApplicationInvalidException(e.getMessage()
                .replaceAll(directory == null ? "" : directory.getAbsolutePath()+File.separator, ""), e);
        }
    }
}
