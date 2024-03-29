package endpoints.datasource;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import endpoints.datadrivencms.DataDrivenCmsDataSourceCommand;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static endpoints.config.ApplicationFactory.dataSourcePostProcessingXsltDir;

/**
 * Fetches and provides XML from a source, for example database or URL.
 * <p>
 * A lot of work of a data source is to fit into the way Endpoints processes data, with the context of the processing,
 * the multi-threaded approach, etc. Therefore if the data produced is the result some complex processing,
 * it is often better to use a separate class concerned just with that, as it's easier to test in isolation,
 * and to see the DataSourceCommand as an "adaptor" (GoF pattern). 
 */
public abstract class DataSourceCommand {

    protected final @Nonnull List<DataSourcePostProcessor> postProcessors;

    @SneakyThrows({SecurityException.class, ClassNotFoundException.class, NoSuchMethodException.class,
        IllegalAccessException.class, InstantiationException.class, InvocationTargetException.class})
    public static @Nonnull DataSourceCommand newForConfig(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element command
    ) throws ConfigurationException {
        try {
            final String className = switch (command.getTagName()) {
                case "parameters" -> ParametersCommand.class.getName();
                case "on-demand-incrementing-number" -> OnDemandIncrementingNumberCommand.class.getName();
                case "literal-xml" -> LiteralXmlCommand.class.getName();
                case "application-introspection" -> ApplicationIntrospectionCommand.class.getName();
                case "xml-from-url" -> XmlFromUrlCommand.class.getName();
                case "xml-from-application" -> XmlFromApplicationCommand.class.getName();
                case "xml-from-database" -> XmlFromDatabaseCommand.class.getName();
                case "md5" -> MD5Command.class.getName();
                case "request-log" -> RequestLogCommand.class.getName();
                case "aws-s3-keys" -> AwsS3KeysCommand.class.getName();
                case "aws-s3-object" -> AwsS3ObjectCommand.class.getName();
                case "data-driven-cms" -> DataDrivenCmsDataSourceCommand.class.getName();
                case "command" -> getMandatoryAttribute(command, "class");
                default -> throw new ConfigurationException("Command tag <" + command.getTagName() + "> unrecognized");
            };

            var constructor = Class.forName(className).getConstructor(XsltCompilationThreads.class, File.class, Element.class);
            return (DataSourceCommand) constructor.newInstance(threads, applicationDir, command);
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof ConfigurationException cause)
                throw new ConfigurationException("<" + command.getTagName() + ">", cause);
            else throw e;
        }
    }

    @SuppressWarnings("unused") // Unused params are used in subclasses
    public DataSourceCommand(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        this.postProcessors = DataSourcePostProcessor.parsePostProcessors(threads, 
            new File(applicationDir, dataSourcePostProcessingXsltDir), config);
    }

    public boolean requiresAwsS3Configuration() { return false; }
    
    /** Checks that no variables other than those supplied are necessary to execute this command */
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException { }
    
    /** Also expands parameters as necessary, as where parameters are to be expanded varies from one data source to another */
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
