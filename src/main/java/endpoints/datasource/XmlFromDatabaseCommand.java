package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.jdbc.DbTransaction.CannotConnectToDatabaseException;
import com.databasesandlife.util.jdbc.DbTransaction.SqlException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.Set;

import static com.databasesandlife.util.DomParser.*;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;

public class XmlFromDatabaseCommand extends DataSourceCommand {
    
    protected final @Nonnull String outputTag;
    protected final @Nonnull String jdbcUrl;
    protected final @Nonnull String sql;
    protected final @Nonnull List<String> paramPatterns;
    
    public XmlFromDatabaseCommand(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, config);

        assertNoOtherElements(config, "post-process", "jdbc-connection-string", "sql", "param");
        outputTag = getOptionalAttribute(config, "tag", "xml-from-database");

        var jdbc = getMandatorySingleSubElement(config, "jdbc-connection-string");
        var envVarName = getOptionalAttribute(jdbc, "from-environment-variable");
        if (envVarName != null) {
            jdbcUrl = System.getenv(envVarName);
            if (jdbcUrl == null) throw new ConfigurationException("JDBC URL specified to come " +
                "from environment variable '" + envVarName + "' but this environment variable is not set");
        }
        else {
            jdbcUrl = jdbc.getTextContent().trim();
            if (jdbcUrl.isEmpty()) throw new ConfigurationException("<jdbc-connection-string> should either have attribute " +
                "from-environment-variable='foo' set or have the JDBC URL as its body");
        }

        sql = getMandatorySingleSubElement(config, "sql").getTextContent();
        paramPatterns = getSubElements(config, "param").stream().map(e -> e.getTextContent()).toList();

        var paramsExpanded = paramPatterns.stream().map(pattern -> null).toArray();
        try { execute(paramsExpanded); }
        catch (SqlException | CannotConnectToDatabaseException e) { throw new ConfigurationException(e); }
    }
    
    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);

        for (var p : paramPatterns) 
            PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues, p, "<param>");
    }

    protected @Nonnull Element[] execute(@Nonnull Object[] paramsExpanded) {
        var resultDocument = DomParser.newDocumentBuilder().newDocument();
        var root = resultDocument.createElement(outputTag);
        resultDocument.appendChild(root);

        try (var db = new DbTransaction(jdbcUrl)) {
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

        return new Element[] { root };
    }
    
    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected Element[] populateOrThrow() {
                var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
                var paramsExpanded = paramPatterns.stream().map(pattern -> replacePlainTextParameters(pattern, stringParams)).toArray();
                return execute(paramsExpanded);
            }
        };
        context.threads.addTaskOffPool(result);
        return result;
    }
}
