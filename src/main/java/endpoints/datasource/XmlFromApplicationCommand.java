package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomParser.getOptionalAttribute;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.lang.Boolean.parseBoolean;

public class XmlFromApplicationCommand extends DataSourceCommand {
    
    protected final @Nonnull File xmlFromApplicationDir;
    protected final @Nonnull String filenamePattern;
    protected final boolean ignoreIfNotFound;

    public XmlFromApplicationCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        this.xmlFromApplicationDir = xmlFromApplicationDir;
        assertNoOtherElements(command, "post-process");
        filenamePattern = getMandatoryAttribute(command, "file");
        ignoreIfNotFound = parseBoolean(getOptionalAttribute(command, "ignore-if-not-found"));
    }

    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);

        var stringKeys = PlaintextParameterReplacer.getKeys(params, visibleIntermediateValues);
        PlaintextParameterReplacer.assertParametersSuffice(stringKeys, filenamePattern, "'file' attribute");
        
        checkContents: if ( ! filenamePattern.contains("$")) {
            try {
                var emptyParams = stringKeys.stream().collect(Collectors.toMap(param -> param, param -> ""));
                var element = executeImmediately(emptyParams);
                if (element == null) break checkContents;
                DomVariableExpander.expand(dollarThenBraces, emptyParams, element);
            }
            catch (VariableNotFoundException | TransformationFailedException e) { throw new ConfigurationException(e); }
        }
    }

    @SneakyThrows(IOException.class)
    protected @CheckForNull Element executeImmediately(@Nonnull Map<String, String> params) throws TransformationFailedException {
        try {
            var leafname = replacePlainTextParameters(filenamePattern, params);
            var file = new File(xmlFromApplicationDir, leafname);

            if ( ! file.getCanonicalPath().startsWith(xmlFromApplicationDir.getCanonicalPath()+File.separator)) {
                LoggerFactory.getLogger(getClass()).warn(getClass().getName()+": Leafname appears to access files outside " +
                    "of base directory: " + leafname);
                throw new TransformationFailedException("Leafname is blocked");
            }

            if ( ! file.exists()) {
                if (ignoreIfNotFound) return null;
                else throw new ConfigurationException("File '" + filenamePattern 
                    + "' not found within 'xml-from-application' directory");
            }
            
            return DomParser.from(file);
        }
        catch (ConfigurationException e) { throw new TransformationFailedException(e); }
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                var stringParams = context.getStringParametersIncludingIntermediateValues(visibleIntermediateValues);
                var fileElement = executeImmediately(stringParams);
                if (fileElement == null) return new Element[0];
                var expanded = DomVariableExpander.expand(dollarThenBraces, stringParams, fileElement).getDocumentElement();
                return new Element[] { expanded };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
