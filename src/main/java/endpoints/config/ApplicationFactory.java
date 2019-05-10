package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.FilenameExtensionFilter;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.DocumentOutputDefinitionParser;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import com.offerready.xslt.config.SecurityParser;
import endpoints.PublishEnvironment;
import endpoints.datasource.DataSource;
import endpoints.datasource.DataSourceCommand;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public abstract class ApplicationFactory extends DocumentOutputDefinitionParser {
    
    @SuppressWarnings("serial")
    public static class ApplicationNotFoundException extends Exception {
        public ApplicationNotFoundException(ApplicationName name) { super(name.name); }
        public ApplicationName getName() { return new ApplicationName(getMessage()); }
    }

    protected static @Nonnull DataSource parseDataSource(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element script
    ) throws ConfigurationException {
        var result = new DataSource();
        if ( ! script.getTagName().equals("data-source")) throw new ConfigurationException("Data source should have root tag " +
            "<data-source> but instead has <"+script.getTagName()+">");
        for (var command : getSubElements(script, "*"))
            result.commands.add(DataSourceCommand.newForConfig(tx, threads, 
                applicationDir, httpXsltDirectory, xmlFromApplicationDir, command));
        return result;
    }
    
    protected static @Nonnull Transformer parseTransformer(
        @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, DataSource> dataSources,
        @Nonnull File application, @Nonnull Element element
    ) throws ConfigurationException {
        var result = new Transformer();
        
        result.source = dataSources.get(getMandatoryAttribute(element, "data-source"));
        if (result.source == null) throw new ConfigurationException("Transformer references data source '"
            +getMandatoryAttribute(element, "data-source")+"' but it cannot be found");
        
        result.defn = parseOutputDefinition(new File(application, "data-source-xslt"), element);
        result.generator = new DocumentGenerator(threads, result.defn);

        var fontsDirectory = new File(application, "fonts");
        var fopConfig = new File(fontsDirectory, "apache-fop-config.xml");
        result.generator.setFopConfigOrNull(
            fontsDirectory.exists() ? fontsDirectory : null,
            fopConfig.exists() ? fopConfig : null);
        
        result.generator.setImagesBase(new File(application, "static"));
        
        return result;
    }
    
    protected static void assertEmailServerConfiguredIfEmailTasks(@Nonnull Application result, @Nonnull EndpointHierarchyFolderNode node)
    throws ConfigurationException {
        for (var child : node.children) {
            if (child instanceof Endpoint) {
                for (var t : ((Endpoint) child).tasks)
                    if (t.requiresEmailServer())
                        if (result.emailServerOrNull == null)
                            throw new ConfigurationException("Application has one or more email tasks, " +
                                "but 'email-sending-configuration.xml' missing");
            }
            else if (child instanceof EndpointHierarchyFolderNode) {
                assertEmailServerConfiguredIfEmailTasks(result, (EndpointHierarchyFolderNode) child);
            }
            else throw new RuntimeException("Unexpected child: "+child.getClass()); 
        }
    }
    
    /** @throws ConfigurationException This loads an application from disk, which might be invalid */
    public static @Nonnull Application loadApplication(
        @Nonnull XsltCompilationThreads threads, @Nonnull DbTransaction tx, @Nonnull File directory
    ) throws ConfigurationException {
        try (var ignored = new Timer("loadApplication '"+directory+"'")) {
            var httpXsltDirectory = new File(directory, "http-xslt");
            var xmlFromApplicationDirectory = new File(directory, "xml-from-application");
            
            var dataSources = new HashMap<String, DataSource>();
            var dataSourcesFiles = new File(directory, "data-sources").listFiles(new FilenameExtensionFilter("xml"));
            if (dataSourcesFiles == null) throw new ConfigurationException("'data-sources' directory missing");
            for (var ds : dataSourcesFiles) {
                try { 
                    DataSource d = parseDataSource(tx, threads, directory, httpXsltDirectory, 
                        xmlFromApplicationDirectory, DomParser.from(ds));
                    dataSources.put(ds.getName().replace(".xml", ""), d); 
                }
                catch (Exception e) { throw new ConfigurationException(ds.getAbsolutePath(), e); }
            }
            
            var transformers = new HashMap<String, Transformer>();
            var transformerFiles = new File(directory, "transformers").listFiles(new FilenameExtensionFilter("xml"));
            if (transformerFiles == null) throw new ConfigurationException("'transformers' directory missing");
            for (var t : transformerFiles) {
                try { transformers.put(t.getName().replace(".xml", ""), 
                    parseTransformer(threads, dataSources, directory, DomParser.from(t))); }
                catch (Exception e) { throw new ConfigurationException(t.getAbsolutePath(), e); }
            }
            
            var emailConfig = new File(directory, "email-sending-configuration.xml");
            var emailServer = emailConfig.exists() ? EmailSendingConfigurationParser.parse(emailConfig) : null;
            if (new File(directory, "smtp.xml").exists())
                throw new ConfigurationException("Legacy 'smtp.xml' exists, rename to 'email-sending-configuration.xml'");

            var result = new Application();
            result.transformers = transformers;
            result.endpoints = EndpointHierarchyParser.parse(tx, threads, transformers, directory, httpXsltDirectory,
                xmlFromApplicationDirectory, new File(directory, "static"), new File(directory, "parameter-xslt"),
                new File(directory, "endpoints.xml"));
            result.secretKeys = SecurityParser.parse(new File(directory, "security.xml"));
            result.emailServerOrNull = emailServer;
            result.servicePortalEndpointMenuItems = new ServicePortalEndpointMenuItemsParser().parse(result.endpoints,
                new File(directory, "service-portal-endpoint-menu-items.xml"));
            
            assertEmailServerConfiguredIfEmailTasks(result, result.endpoints);
            
            return result;
        }
    }
    
    public abstract @Nonnull Application getApplication(
        @Nonnull DbTransaction db,
        @Nonnull ApplicationName name, @Nonnull PublishEnvironment environment
    ) throws ApplicationNotFoundException;
}
