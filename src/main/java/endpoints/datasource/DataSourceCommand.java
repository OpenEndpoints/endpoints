package endpoints.datasource;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getMandatoryAttribute;

public abstract class DataSourceCommand {

    @SneakyThrows({SecurityException.class, ClassNotFoundException.class, NoSuchMethodException.class,
        IllegalAccessException.class, InstantiationException.class, InvocationTargetException.class})
    public static @Nonnull DataSourceCommand newForConfig(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element command
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
                File.class, File.class, File.class, Element.class);
            return (DataSourceCommand) constructor.newInstance(tx, threads,
                applicationDir, httpXsltDirectory, xmlFromApplicationDir, command);
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof ConfigurationException) throw (ConfigurationException) e.getCause();
            else throw e;
        }

    }

    // Subclass must have this constructor as it's called by reflection
    public DataSourceCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element config
    ) throws ConfigurationException { }
    
    /** Checks that no variables other than those supplied are necessary to execute this command */
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params)
    throws ConfigurationException { }
    
    /**
     * A future is returned here, so that all data source commands (for example fetching various URLs) can execute in parallel
     * @return parameters have been expanded in resulting XML if necessary
     */
    abstract public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) 
    throws TransformationFailedException;
}
