package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.databasesandlife.util.DomParser.getSubElements;

public class DataSource {
    
    protected final @Nonnull List<DataSourceCommand> commands = new ArrayList<>();

    public DataSource() { }

    public DataSource(
        @Nonnull DbTransaction tx, @Nonnull WeaklyCachedXsltTransformer.XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element script
    ) throws ConfigurationException {
        if ( ! script.getTagName().equals("data-source")) throw new ConfigurationException("Data source should have root tag " +
            "<data-source> but instead has <"+script.getTagName()+">");
        for (var command : getSubElements(script, "*"))
            commands.add(DataSourceCommand.newForConfig(tx, threads,
                applicationDir, httpXsltDirectory, xmlFromApplicationDir, command));
    }

    /** Checks that no variables other than those supplied are necessary to execute all commands */
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        for (var c : commands) {
            try { c.assertParametersSuffice(params, visibleIntermediateValues); }
            catch (ConfigurationException e) { throw new ConfigurationException(c.getClass().getSimpleName(), e); }
        }
    }

    /** @param visibleIntermediateValues these values are already produced by the time this method is called. */
    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull Consumer<Document> afterDataSource
    ) throws TransformationFailedException {
        var futures = new ArrayList<DataSourceCommandFetcher>(commands.size());
        for (var c : commands) futures.add(c.scheduleExecution(context, visibleIntermediateValues));
        
        Runnable createDocument = () -> {
            var result = DomParser.newDocumentBuilder().newDocument();
            result.appendChild(result.createElement("transformation-input"));
            for (var r : futures) 
                for (var element : r.get())
                    result.getDocumentElement().appendChild(result.importNode(element, true));
            afterDataSource.accept(result);
        };
        context.threads.addTaskWithDependencies(futures, createDocument);

        return createDocument;
    }
}
