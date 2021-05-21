package endpoints;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.CleartextPassword;
import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.ApplicationName;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

@RequiredArgsConstructor
public class GitApplicationRepository {

    public final @Nonnull String url;
    public final @CheckForNull String username;
    public final @CheckForNull CleartextPassword password;

    /** Represents a problem with Git or with the application logic provided by {@link GitApplicationRepository} */
    public static class RepositoryCommandFailedException extends Exception {
        RepositoryCommandFailedException(String x) { super(x); }
        RepositoryCommandFailedException(Throwable x) { super(x); }
    }

    public GitApplicationRepository(@Nonnull ApplicationConfigRecord row) {
        url = row.getGitUrl();
        username = row.getGitUsername();
        password = row.getGitPasswordCleartext();
    }

    public static @Nonnull GitApplicationRepository fetch(@Nonnull DbTransaction tx, @Nonnull ApplicationName application) {
        return tx.jooq().fetchSingle(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(application))
            .map(r -> new GitApplicationRepository((ApplicationConfigRecord) r));
    }

    public static @Nonnull Map<ApplicationName, GitApplicationRepository> fetchAll(@Nonnull DbTransaction tx) {
        return tx.jooq().fetch(APPLICATION_CONFIG).intoMap(APPLICATION_CONFIG.APPLICATION_NAME, GitApplicationRepository::new);
    }

    protected @Nonnull File newTemporaryDirectory(@Nonnull File destination) {
        return new File(destination.getParentFile(), "tmp-" + RandomStringUtils.randomAlphabetic(12));
    }

    protected @CheckForNull CredentialsProvider getCredentialsProvider() {
        if (username != null && password != null) return new UsernamePasswordCredentialsProvider(username, password.getCleartext());
        else return null;
    }

    protected void asyncDeleteDirectory(@Nonnull File dir) {
        var runnable = new Runnable() {
            @Override public void run() {
                try { FileUtils.deleteDirectory(dir); }
                catch (Exception e) {
                    Logger.getLogger(getClass()).warn("Uncaught exception while deleting temporary directory '" + dir + "'", e);
                }
            }
        };

        var t = new Thread(runnable);
        t.setName("Delete tmp dir");
        t.start();
    }

    public @Nonnull GitRevision fetchLatestRevision() throws RepositoryCommandFailedException {
        try {
            var map = Git.lsRemoteRepository()
                .setRemote(url)
                .setCredentialsProvider(getCredentialsProvider())
                .setTags(false)
                .setHeads(false)
                .callAsMap();
            var result = map.get("HEAD");
            if (result == null) throw new RepositoryCommandFailedException("Cannot find default branch (e.g. 'main' or 'master')");
            return new GitRevision(result.getObjectId().getName());
        }
        catch (GitAPIException e) { throw new RepositoryCommandFailedException(e); }
    }

    @SneakyThrows(IOException.class)
    protected void checkoutAtomically(@Nonnull GitRevision revision, @Nonnull File destination) 
    throws RepositoryCommandFailedException {
        var tmpDir = newTemporaryDirectory(destination);
        try (var ignored = new Timer("Git checkout of application '" + url + "'")) {
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(tmpDir)
                .setCredentialsProvider(getCredentialsProvider())
                .call();

            Git.open(tmpDir)
                .checkout()
                .setName(revision.sha256Hex)
                .call();

            try {
                Files.move(tmpDir.toPath(), destination.toPath(), ATOMIC_MOVE);
            }
            catch (IOException e) {
                // Race condition, somebody else has just checked this out at the same time
                // As checkouts are immutable, their checkout is just as good as ours
                if ( ! destination.isDirectory()) throw e;
            }
        }
        catch (GitAPIException e) { throw new RepositoryCommandFailedException(e); }
        finally { asyncDeleteDirectory(tmpDir); }
    }

    /**
     * Only checkout the particular revision of the application to disk if it isn't already there.
     * For example a previous "publish" on the same server would mean it's already there,
     * however a restart of an AWS instance means new blank disks meaning it's not there currently.
     */
    public void checkoutAtomicallyIfNecessary(@Nonnull GitRevision revision, @Nonnull File destination) 
    throws RepositoryCommandFailedException {
        if ( ! destination.exists()) checkoutAtomically(revision, destination);
    }

    @SneakyThrows(IOException.class)
    public void checkoutAlterAndCommit(
        @Nonnull String gitUsername, @Nonnull String commitMessage, @Nonnull Consumer<File> alteration
    )
    throws RepositoryCommandFailedException {
        var tmpDir = File.createTempFile("git", "");
        if (!tmpDir.delete()) throw new RepositoryCommandFailedException("Cannot delete tmp file");

        try (var ignored = new Timer("checkoutAlterAndCommit")) {
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(tmpDir)
                .setCredentialsProvider(getCredentialsProvider())
                .call();

            alteration.accept(tmpDir);

            var checkout = Git.open(tmpDir);

            checkout.add().addFilepattern(".").call();
            checkout.commit().setAuthor(gitUsername, "").setMessage(commitMessage).call();
            checkout.push().setCredentialsProvider(getCredentialsProvider()).call();
        }
        catch (GitAPIException e) { throw new RepositoryCommandFailedException(e); }
        finally { asyncDeleteDirectory(tmpDir); }
    }
}
