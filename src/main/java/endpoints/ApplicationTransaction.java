package endpoints;

import com.databasesandlife.util.EmailTransaction;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.Application;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

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
    
    public final @Nonnull DbTransaction db = DeploymentParameters.get().newDbTransaction();
    protected final @Nonnull List<DbTransaction> additionalDbs = new ArrayList<>();
    public final @CheckForNull EmailTransaction email;
    
    public ApplicationTransaction(@Nonnull Application a) throws ConfigurationException {
        if (a.getEmailServerOrNull() == null) email = null;
        else email = new EmailTransaction(a.getEmailServerOrNull());
    }

    @SuppressWarnings("unused") // Used to be used when tasks could specify arbitrary DB connections, maybe useful in the future?
    public synchronized void addDatabaseConnection(@Nonnull DbTransaction db) {
        additionalDbs.add(db);
    }

    public void commit() {
        if (email != null) email.commit();
        db.commit();
        additionalDbs.forEach(db -> db.commit());
    }
    
    @Override public void close() { 
        db.close();
        additionalDbs.forEach(db -> db.close());
    }
}
