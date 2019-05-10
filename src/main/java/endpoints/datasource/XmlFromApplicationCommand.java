package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomParser.getOptionalAttribute;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.lang.Boolean.parseBoolean;

public class XmlFromApplicationCommand extends DataSourceCommand {
    
    protected final @Nonnull File xmlFromApplicationDir;
    protected @Nonnull String filenamePattern;
    protected boolean ignoreIfNotFound;

    public XmlFromApplicationCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, command);
        this.xmlFromApplicationDir = xmlFromApplicationDir;
        assertNoOtherElements(command);
        filenamePattern = getMandatoryAttribute(command, "file");
        ignoreIfNotFound = parseBoolean(getOptionalAttribute(command, "ignore-if-not-found"));
    }

    @Override
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        PlaintextParameterReplacer.assertParametersSuffice(params, filenamePattern, "'file' attribute");
        
        checkContents: if ( ! filenamePattern.contains("$")) {
            try {
                var element = executeImmediately(params.stream().collect(Collectors.toMap(param -> param, param -> "")));
                if (element == null) break checkContents;
                var emptyParams = params.stream().collect(Collectors.toMap(param -> param.name, param -> ""));
                DomVariableExpander.expand(dollarThenBraces, emptyParams, element);
            }
            catch (VariableNotFoundException | TransformationFailedException e) { throw new ConfigurationException(e); }
        }
    }

    @SneakyThrows(IOException.class)
    protected @CheckForNull Element executeImmediately(@Nonnull Map<ParameterName, String> params) throws TransformationFailedException {
        try {
            var leafname = replacePlainTextParameters(filenamePattern, params);
            var file = new File(xmlFromApplicationDir, leafname);

            if ( ! file.getCanonicalPath().startsWith(xmlFromApplicationDir.getCanonicalPath()+File.separator)) {
                Logger.getLogger(getClass()).warn(getClass().getName()+": Leafname appears to access files outside " +
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
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                var stringParams = context.params.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue()));
                var fileElement = executeImmediately(context.params);
                if (fileElement == null) return new Element[0];
                var expanded = DomVariableExpander.expand(dollarThenBraces, stringParams, fileElement).getDocumentElement();
                return new Element[] { expanded };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
