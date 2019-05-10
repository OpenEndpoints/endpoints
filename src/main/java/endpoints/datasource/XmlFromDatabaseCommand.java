package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.CannotConnectToDatabaseException;
import com.databasesandlife.util.jdbc.DbTransaction.SqlException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;

public class XmlFromDatabaseCommand extends DataSourceCommand {
    
    protected @Nonnull String outputTag;
    protected @CheckForNull String jdbcUrlOrNull;
    protected @Nonnull String sql;
    protected @Nonnull List<String> paramPatterns;
    
    public XmlFromDatabaseCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, config);

        assertNoOtherElements(config, "jdbc-connection-string", "sql", "param");
        outputTag = getOptionalAttribute(config, "tag", "xml-from-database");

        var jdbc = getOptionalSingleSubElement(config, "jdbc-connection-string");
        if (jdbc != null) {
            var envVarName = getOptionalAttribute(jdbc, "from-environment-variable");
            if (envVarName != null) {
                jdbcUrlOrNull = System.getenv(envVarName);
                if (jdbcUrlOrNull == null) throw new ConfigurationException("JDBC URL specified to come " +
                    "from environment variable '" + envVarName + "' but this environment variable is not set");
            }
            else jdbcUrlOrNull = jdbc.getTextContent().trim();
        }

        sql = getMandatorySingleSubElement(config, "sql").getTextContent();
        paramPatterns = getSubElements(config, "param").stream().map(e -> e.getTextContent()).collect(Collectors.toList());

        var paramsExpanded = paramPatterns.stream().map(pattern -> null).toArray();
        try { execute(tx, paramsExpanded); }
        catch (SqlException | CannotConnectToDatabaseException e) { throw new ConfigurationException(e); }
    }
    
    @Override
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        for (var p : paramPatterns) PlaintextParameterReplacer.assertParametersSuffice(params, p, "<param>");
    }

    protected @Nonnull Element[] execute(@Nonnull DbTransaction tx, @Nonnull Object[] paramsExpanded) {
        var resultDocument = DomParser.newDocumentBuilder().newDocument();
        var root = resultDocument.createElement(outputTag);
        resultDocument.appendChild(root);

        var db = jdbcUrlOrNull == null ? tx : new DbTransaction(jdbcUrlOrNull);
        synchronized (db) {
            for (var row : db.query(sql, paramsExpanded)) {
                var rowElement = resultDocument.createElement("row");
                root.appendChild(rowElement);
                for (var col : row.getColumnNames()) {
                    var colElement = resultDocument.createElement(col);
                    rowElement.appendChild(colElement);
                    colElement.setTextContent(row.getString(col));
                }
            }
        }
        if (jdbcUrlOrNull != null) db.close();

        return new Element[] { root };
    }
    
    @Override
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected Element[] populateOrThrow() {
                var paramsExpanded = paramPatterns.stream().map(pattern -> replacePlainTextParameters(pattern, context.params)).toArray();
                return execute(context.tx.db, paramsExpanded);
            }
        };
        context.threads.addTaskOffPool(result);
        return result;
    }
}
