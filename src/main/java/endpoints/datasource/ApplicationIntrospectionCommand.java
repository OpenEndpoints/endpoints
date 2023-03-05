package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

public class ApplicationIntrospectionCommand extends DataSourceCommand {

    final @Nonnull File xmlFromApplicationDir;
    final @Nonnull File dir;

    public ApplicationIntrospectionCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        this.xmlFromApplicationDir = xmlFromApplicationDir;
        this.dir = applicationDir;
    }

    /** Adds all children of "directory" as children to "container" */
    protected void addChildElements(@Nonnull Element container, @Nonnull File directory)
    throws TransformationFailedException {
        var children = directory.listFiles();
        if (children == null) throw new TransformationFailedException(directory + " does not exist");
        for (var childFile : children) {
            final Element childElement;
            if (childFile.isDirectory()) {
                childElement = container.getOwnerDocument().createElement("directory");
                addChildElements(childElement, childFile);
            } else if (childFile.isFile()) {
                if (childFile.getName().toLowerCase().endsWith(".xml")) {
                    try {
                        childElement = container.getOwnerDocument().createElement("xml-file");
                        if ( ! childFile.getAbsolutePath().startsWith(xmlFromApplicationDir.getAbsolutePath())) {
                            var fileXml = DomParser.from(childFile);
                            childElement.appendChild(container.getOwnerDocument().importNode(fileXml, true));
                        }
                    }
                    catch (ConfigurationException e) { throw new TransformationFailedException(e); }
                } else {
                    childElement = container.getOwnerDocument().createElement("file");
                }
            } else continue;
            childElement.setAttribute("name", childFile.getName());
            container.appendChild(childElement);
        }
    }

    @Override 
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                var result = DomParser.newDocumentBuilder().newDocument().createElement("application-introspection");
                addChildElements(result, dir);
                return new Element[] { result };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
