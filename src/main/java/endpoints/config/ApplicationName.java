package endpoints.config;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.GitRevision;
import endpoints.PublishEnvironment;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.generated.jooq.tables.records.ApplicationPublishRecord;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.UUID;

public record ApplicationName(
    @Nonnull String name
) implements Serializable {
    public static @Nonnull ApplicationName newRandomForTesting() {
        return new ApplicationName(UUID.randomUUID().toString());
    }

    public void insertToDbForTesting(@Nonnull DbTransaction tx, @Nonnull PublishEnvironment environment) {
        var config = new ApplicationConfigRecord();
        config.setApplicationName(this);
        config.setDisplayName(name());
        config.setGitUrl("TEST");
        tx.insert(config);

        var publish = new ApplicationPublishRecord();
        publish.setApplicationName(this);
        publish.setRevision(new GitRevision("123"));
        publish.setEnvironment(environment);
        tx.insert(publish);
    }
}
