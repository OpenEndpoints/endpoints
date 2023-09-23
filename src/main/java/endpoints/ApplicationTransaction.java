package endpoints;

import com.databasesandlife.util.EmailTransaction;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.Application;
import endpoints.config.ApplicationName;
import endpoints.config.IntermediateValueName;

import javax.annotation.Nonnull;
import java.util.*;

import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;

/**
 * Represents a unit of work a user wishes to perform.
 *    <p>
 * Allows atomic operations encompassing:
 * <ul>
 * <li>Main database
 * <li>Sending emails
 * <li>Any additional databases added via {@link #addDatabaseConnection(DbTransaction)}.
 * </ul>
 *    <p>
 * When the transaction is committed, all databases are committed and emails are sent.
 * When the transaction is rolled back, all databases are rolled back and emails are discarded.
 * This is a simplified version of the concept of "distributed transactions" found e.g. in J2EE or CORBA.
 *    <p>
 * The email server is defined per application, so this transaction is application-specific.
 */
public class ApplicationTransaction implements AutoCloseable {
    
    protected final @Nonnull Application application;
    public final @Nonnull DbTransaction db = DeploymentParameters.get().newDbTransaction();
    protected final @Nonnull List<DbTransaction> additionalDbs = new ArrayList<>();
    protected final @Nonnull Map<Set<String>, EmailTransaction> email = new HashMap<>();
    
    public ApplicationTransaction(@Nonnull Application a) {
        this.application = a;

        // This is necessary so that "SELECT FOR UPDATE" can be used, 
        // and that committed values from other transactions can be read after the lock is acquired.
        // (With REPEATABLE READ, even after SELECT FOR UPDATE acquires the lock, values from the start of the tx are read.)
        db.execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED");
    }

    public void acquireLock(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName
    ) {
        try (var ignored3 = new Timer("Acquire lock on '" + applicationName.name()
            + "', environment '" + environment.name() + "'")) {
            db.jooq().select().from(APPLICATION_PUBLISH)
                .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(applicationName))
                .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                .forUpdate().fetchSingle();
        }
    }

    @SuppressWarnings("unused") // Used to be used when tasks could specify arbitrary DB connections, maybe useful in the future?
    public synchronized void addDatabaseConnection(@Nonnull DbTransaction db) {
        additionalDbs.add(db);
    }
    
    public @Nonnull EmailTransaction getEmailTransaction(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
        
        synchronized (this) {
            if ( ! email.containsKey(stringParams.keySet())) {
                var config = application.getEmailConfigurationOrNull();
                assert config != null : "should have been checked on application load";

                var tx = new EmailTransaction(config.generate(context, visibleIntermediateValues));
                email.put(stringParams.keySet(), tx);
            }
            return email.get(stringParams.keySet());
        }
    }

    public void commit() {
        email.values().forEach(e -> e.commit()); // more likely to go wrong, so place higher
        db.commit();
        additionalDbs.forEach(db -> db.commit());
    }
    
    @Override public void close() { 
        db.close();
        additionalDbs.forEach(db -> db.close());
    }
}
