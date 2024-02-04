package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.FilenameExtensionFilter;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import com.offerready.xslt.parser.DocumentOutputDefinitionParser;
import com.offerready.xslt.parser.SecurityParser;
import endpoints.GitRevision;
import endpoints.PublishEnvironment;
import endpoints.datasource.DataSource;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public abstract class ApplicationFactory extends DocumentOutputDefinitionParser {
    
    public static class ApplicationNotFoundException extends Exception {
        public ApplicationNotFoundException(ApplicationName a) { super(a.name()); }
        public ApplicationName getName() { return new ApplicationName(getMessage()); }
    }
    
    public record ApplicationConfig(
        boolean locked, 
        boolean debugAllowed
    ) { }

    public static final String endpointXmlFilename = "endpoints.xml";
    public static final String ooxmlResponsesDir = "ooxml-responses";
    public static final String staticDir = "static";
    public static final String parameterXsltDir = "parameter-xslt";
    public static final String httpXsltDir = "http-xslt";
    public static final String xmlFromApplicationDir = "xml-from-application";
    public static final String dataSourcePostProcessingXsltDir = "data-source-post-processing-xslt";
    public static final String dataDrivenCmsDir = "data-driven-cms";

    protected static @Nonnull Transformer parseTransformer(
        @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, DataSource> dataSources,
        @Nonnull File application, @Nonnull Element element
    ) throws ConfigurationException {
        var result = new Transformer();
        
        result.sourceName = getMandatoryAttribute(element, "data-source");
        result.source = dataSources.get(result.sourceName);
        if (result.source == null) throw new ConfigurationException("Transformer references data source '"
            +getMandatoryAttribute(element, "data-source")+"' but it cannot be found");
        
        var writeInputToAwsS3 = getOptionalSingleSubElement(element, "write-input-to-aws-s3");
        if (writeInputToAwsS3 != null) result.writeInputToAwsS3 = new WriteTransformationDataToAwsS3Command(writeInputToAwsS3);

        var writeOutputToAwsS3 = getOptionalSingleSubElement(element, "write-output-to-aws-s3");
        if (writeOutputToAwsS3 != null) result.writeOutputToAwsS3 = new WriteTransformationDataToAwsS3Command(writeOutputToAwsS3);

        result.defn = parseDocumentOutputDefinition(new File(application, "data-source-xslt"), element);
        result.generator = new DocumentGenerator(threads, result.defn);

        var fontsDirectory = new File(application, "fonts");
        var fopConfig = new File(fontsDirectory, "apache-fop-config.xml");
        result.generator.setFopConfigOrNull(
            fontsDirectory.exists() ? fontsDirectory : null,
            fopConfig.exists() ? fopConfig : null);
        
        result.generator.setImagesBase(new File(application, staticDir));
        
        return result;
    }
    
    protected static void assertCompatibleWithEmailConfig(
        @CheckForNull EmailSendingConfigurationFactory config, @Nonnull EndpointHierarchyFolderNode node
    ) throws ConfigurationException {
        for (var child : node.children) {
            switch (child) {
                case Endpoint e -> {
                    try {
                        for (var t : e.tasks)
                            try { t.assertCompatibleWithEmailConfig(config, e.aggregateParametersOverParents().keySet()); }
                            catch (ConfigurationException ex) { throw new ConfigurationException(t.getHumanReadableId(), ex); }
                    }
                    catch (ConfigurationException ex) { throw new ConfigurationException("Endpoint '" + e.name.name + "'", ex); }
                }
                case EndpointHierarchyFolderNode f -> assertCompatibleWithEmailConfig(config, f);
                default -> throw new RuntimeException("Unexpected child: " + child.getClass());
            }
        }
    }

    protected static void assertNoDataSourceAwsS3ConfigurationNeeded(@Nonnull Map<String, DataSource> dataSources) 
    throws ConfigurationException {
        for (var ds : dataSources.entrySet())
            if (ds.getValue().requiresAwsS3Configuration())
                throw new ConfigurationException("Data source '" + ds.getKey() + "' " +
                    "requires 'aws-s3-configuration.xml' but no such configuration was found");
    }
    
    protected static void assertNoEndpointAwsS3ConfigurationNeeded(@Nonnull EndpointHierarchyFolderNode node) 
    throws ConfigurationException {
        for (var child : node.children) {
            switch (child) {
                case Endpoint e -> {
                    if (e.parameterTransformation != null)
                        for (var ds : e.parameterTransformation.dataSourceCommands)
                            if (ds.requiresAwsS3Configuration())
                                throw new ConfigurationException("A data source for parameter transformation for endpoint " +
                                    "'" + e.name + "' requires 'aws-s3-configuration.xml' but no such configuration was found");
                }
                case EndpointHierarchyFolderNode f -> assertNoEndpointAwsS3ConfigurationNeeded(f);
                default -> throw new RuntimeException("Unexpected child: " + child.getClass());
            }
        }
    }

    protected static void assertNoTransformerAwsS3ConfigurationNeeded(@Nonnull Map<String, Transformer> transformers)
    throws ConfigurationException {
        for (var t : transformers.entrySet())
            if (t.getValue().requiresAwsS3Configuration())
                throw new ConfigurationException("Transformer '" + t.getKey() + "' needs AWS S3 configuration, " +
                    "yet none was supplied");
    }

    /** @throws ConfigurationException This loads an application from disk, which might be invalid */
    public static @Nonnull Application loadApplication(
        @Nonnull XsltCompilationThreads threads, @CheckForNull GitRevision revision, @Nonnull File directory
    ) 
    throws ConfigurationException {
        try (var ignored = new Timer("loadApplication '"+directory+"'")) {
            var dataSources = new HashMap<String, DataSource>();
            var dataSourcesFiles = new File(directory, "data-sources").listFiles(new FilenameExtensionFilter("xml"));
            if (dataSourcesFiles == null) throw new ConfigurationException("'data-sources' directory missing");
            for (var ds : dataSourcesFiles) {
                try { 
                    var d = new DataSource(threads, directory, DomParser.from(ds));
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
            
            var emailConfigFile = new File(directory, "email-sending-configuration.xml");
            var emailConfig = emailConfigFile.exists() ? new EmailSendingConfigurationFactory(emailConfigFile) : null;
            if (new File(directory, "smtp.xml").exists())
                throw new ConfigurationException("Legacy 'smtp.xml' exists, rename to 'email-sending-configuration.xml'");
            
            var awsS3ConfigFile = new File(directory, "aws-s3-configuration.xml");
            var awsS3Config = awsS3ConfigFile.exists() ? AwsS3Configuration.parse(awsS3ConfigFile) : null;

            var result = new Application();
            result.revision = revision;
            result.transformers = transformers;
            result.endpoints = EndpointHierarchyParser.parse(threads, transformers, directory);
            result.secretKeys = SecurityParser.parse(new File(directory, "security.xml"));
            result.emailConfigurationOrNull = emailConfig;
            result.awsS3ConfigurationOrNull = awsS3Config;
            result.secrets = new AwsSecretsToParameterMapping(new File(directory, "secrets.xml"));
            result.servicePortalEndpointMenuItems = new ServicePortalEndpointMenuItemsParser().parse(result.endpoints,
                new File(directory, "service-portal-endpoint-menu-items.xml"));
            
            assertCompatibleWithEmailConfig(result.emailConfigurationOrNull, result.endpoints);
            
            if (awsS3Config == null) {
                assertNoDataSourceAwsS3ConfigurationNeeded(dataSources);
                assertNoTransformerAwsS3ConfigurationNeeded(transformers);
                assertNoEndpointAwsS3ConfigurationNeeded(result.endpoints);
            }
            
            return result;
        }
    }
    
    public abstract @Nonnull Application getApplication(
        @Nonnull DbTransaction db,
        @Nonnull ApplicationName name, @Nonnull PublishEnvironment environment
    ) throws ApplicationNotFoundException;
    
    public abstract @Nonnull ApplicationConfig fetchApplicationConfig(@Nonnull DbTransaction db, @Nonnull ApplicationName name);
}
