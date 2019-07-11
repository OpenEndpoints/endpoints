package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Document;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DataSource {
    
    public final @Nonnull List<DataSourceCommand> commands = new ArrayList<>();

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
        var futures = new ArrayList<DataSourceCommandResult>(commands.size());
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
