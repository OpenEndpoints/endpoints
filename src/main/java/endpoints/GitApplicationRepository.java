package endpoints;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.config.ApplicationName;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

public class GitApplicationRepository {

    public final @Nonnull String info;
    public final @CheckForNull String username, password;

    /** Represents a problem with Git or with the application logic provided by {@link GitApplicationRepository} */
    public static class RepositoryCommandFailedException extends Exception {
        RepositoryCommandFailedException(String x) { super(x); }
        RepositoryCommandFailedException(Throwable x) { super(x); }
    }

    public GitApplicationRepository(@Nonnull String pipeSeparated) throws ConfigurationException {
        var m = Pattern.compile("^([^|]+?)(\\|([^|]+)\\|([^|]+))?$").matcher(pipeSeparated);
        if (! m.matches()) throw new ConfigurationException("Git '" + pipeSeparated + "' should have 'url|user|pw' form");
        info = m.group(1);
        username = m.group(3);
        password = m.group(4);
    }

    protected @Nonnull File newTemporaryDirectory(@Nonnull File destination, @Nonnull String hint) {
        return new File(destination.getParentFile(), "tmp-" + RandomStringUtils.randomAlphabetic(6) + "-" + hint);
    }

    protected @CheckForNull CredentialsProvider getCredentialsProvider() {
        if (username != null && password != null) return new UsernamePasswordCredentialsProvider(username, password);
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
                .setRemote(info)
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
    protected void checkoutAtomically(
        @Nonnull ApplicationName application, @Nonnull GitRevision revision, @Nonnull File destination
    ) throws RepositoryCommandFailedException {
        var tmpDir = newTemporaryDirectory(destination, "git-" + application.name + "-" + revision.sha256Hex);
        try (var ignored = new Timer("Git checkout of application '" + application.name + "'")) {
            Git.cloneRepository()
                .setURI(info)
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
    public void checkoutAtomicallyIfNecessary(
        @Nonnull ApplicationName application,
        @Nonnull GitRevision revision, @Nonnull File destination
    ) throws RepositoryCommandFailedException {
        if ( ! destination.exists()) checkoutAtomically(application, revision, destination);
    }

    @SneakyThrows(IOException.class)
    public void checkoutAlterAndCommit(
        @Nonnull ApplicationName application, @Nonnull String gitUsername,
        @Nonnull String commitMessage, @Nonnull Consumer<File> alteration
    )
    throws RepositoryCommandFailedException {
        var tmpDir = File.createTempFile("git", "");
        if (!tmpDir.delete()) throw new RepositoryCommandFailedException("Cannot delete tmp file");

        try (var ignored = new Timer("checkoutAlterAndCommit")) {
            Git.cloneRepository()
                .setURI(info)
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
