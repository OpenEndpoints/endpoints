package endpoints.datasource;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomParser.getSubElements;

public abstract class DataSourceCommand {

    protected final @Nonnull List<DataSourcePostProcessor> postProcessors;

    @SneakyThrows({SecurityException.class, ClassNotFoundException.class, NoSuchMethodException.class,
        IllegalAccessException.class, InstantiationException.class, InvocationTargetException.class})
    public static @Nonnull DataSourceCommand newForConfig(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        try {
            final String className;
            switch (command.getTagName()) {
                case "parameters": className = ParametersCommand.class.getName(); break;
                case "on-demand-incrementing-number": className = OnDemandIncrementingNumberCommand.class.getName(); break;
                case "literal-xml": className = LiteralXmlCommand.class.getName(); break;
                case "application-introspection": className = ApplicationIntrospectionCommand.class.getName(); break;
                case "xml-from-url": className = XmlFromUrlCommand.class.getName(); break;
                case "xml-from-application": className = XmlFromApplicationCommand.class.getName(); break;
                case "xml-from-database": className = XmlFromDatabaseCommand.class.getName(); break;
                case "md5": className = MD5Command.class.getName(); break;
                case "command": className = getMandatoryAttribute(command, "class"); break;
                default: throw new ConfigurationException("Command tag <"+command.getTagName()+"> unrecognized");
            }

            var constructor = Class.forName(className).getConstructor(DbTransaction.class, XsltCompilationThreads.class,
                File.class, File.class, File.class, File.class, Element.class);
            return (DataSourceCommand) constructor.newInstance(tx, threads,
                applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof ConfigurationException)
                throw new ConfigurationException("<" + command.getTagName() + ">", e.getCause());
            else throw e;
        }
    }

    @SuppressWarnings("unused") // Unused params are used in subclasses
    public DataSourceCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element config
    ) throws ConfigurationException {
        this.postProcessors = DataSourcePostProcessor.parsePostProcessors(threads, dataSourcePostProcessingXsltDir, config);
    }
    
    /** Checks that no variables other than those supplied are necessary to execute this command */
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException { }
    
    abstract public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException;
    
    protected @Nonnull DataSourceCommandFetcher schedulePostProcessing(
        @Nonnull TransformationContext context,
        @Nonnull DataSourceCommandFetcher fetched
    ) {
        return context.threads.addTaskWithDependencies(List.of(fetched), new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                return DataSourcePostProcessor.postProcess(postProcessors, fetched.result);
            }
        });
    }

    public @Nonnull DataSourceCommandFetcher scheduleExecution(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var fetched = scheduleFetch(context, visibleIntermediateValues);
        return schedulePostProcessing(context, fetched);
    }
}
