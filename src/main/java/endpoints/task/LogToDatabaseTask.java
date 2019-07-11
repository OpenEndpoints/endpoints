package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.SqlException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.*;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;

/** Writes into a database. See "configuration.md" for more details. */
public class LogToDatabaseTask extends Task {
    
    protected @CheckForNull String jdbcUrlOrNull;
    protected @Nonnull String table;
    protected @Nonnull final Map<String, String> patternForColumn = new HashMap<>();

    public LogToDatabaseTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir, @Nonnull Element config
    )
    throws ConfigurationException {
        super(threads, httpXsltDirectory, transformers, staticDir, config);

        assertNoOtherElements(config, "input-intermediate-value", "jdbc-connection-string", "table", "column");
        
        var jdbc = getOptionalSingleSubElement(config, "jdbc-connection-string");
        if (jdbc != null) jdbcUrlOrNull = jdbc.getTextContent().trim();
        table = getMandatorySingleSubElement(config, "table").getTextContent();
        for (var p : getSubElements(config, "column")) {
            String name = getMandatoryAttribute(p, "name");
            String pattern = p.getTextContent().trim().isEmpty() ? ("${" + name + "}") : p.getTextContent();
            patternForColumn.put(name, pattern);
        }
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        for (var pattern : patternForColumn.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues,
                pattern.getValue(), "<column name='"+pattern.getKey()+"'>");
    }
    
    @Override
    protected void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    ) {
        final DbTransaction db;
        if (jdbcUrlOrNull == null) db = context.tx.db;
        else context.tx.addDatabaseConnection(db = new DbTransaction(jdbcUrlOrNull));

        try {
            var stringParams = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);
            var cols = patternForColumn.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                e -> replacePlainTextParameters(e.getValue(), stringParams)));
            db.insert(table, cols);
        }
        catch (SqlException e) {
            throw new RuntimeException(new TaskExecutionFailedException("Error occurred inserting to table '"+table+"'", e));
        }
        
        context.threads.addTask(workComplete);
    }
}
