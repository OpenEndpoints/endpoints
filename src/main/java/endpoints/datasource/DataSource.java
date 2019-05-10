package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.ApplicationTransaction;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import org.w3c.dom.Document;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class DataSource {
    
    public final @Nonnull List<DataSourceCommand> commands = new ArrayList<>();

    /** Checks that no variables other than those supplied are necessary to execute all commands */
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        for (var c : commands) {
            try { c.assertParametersSuffice(params); }
            catch (ConfigurationException e) { throw new ConfigurationException(c.getClass().getSimpleName(), e); }
        }
    }

    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context, @Nonnull Consumer<Document> afterDataSource
    ) throws TransformationFailedException {
        var futures = new ArrayList<DataSourceCommandResult>(commands.size());
        for (var c : commands) futures.add(c.scheduleExecution(context));
        
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
