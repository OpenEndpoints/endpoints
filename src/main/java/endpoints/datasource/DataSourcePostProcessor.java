package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.DocumentGenerator;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import lombok.SneakyThrows;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.databasesandlife.util.DomParser.getSubElements;
import static com.offerready.xslt.WeaklyCachedXsltTransformer.getTransformerOrScheduleCompilation;

public class DataSourcePostProcessor {

    protected final @Nonnull WeaklyCachedXsltTransformer xslt;
    
    public DataSourcePostProcessor(
        @Nonnull XsltCompilationThreads threads, @Nonnull File dataSourcePostProcessingXsltDir,
        @Nonnull Element postProcessElement
    ) throws ConfigurationException {
        var xsltName = DomParser.getMandatoryAttribute(postProcessElement, "xslt");
        var xsltFile = new File(dataSourcePostProcessingXsltDir, xsltName);
        if ( ! xsltFile.exists()) throw new ConfigurationException("XSLT file '"+xsltName+"' not found");
        xslt = getTransformerOrScheduleCompilation(threads, xsltFile.getAbsolutePath(),
            new DocumentGenerator.StyleVisionXslt(xsltFile));
    }

    protected static @Nonnull List<DataSourcePostProcessor> parsePostProcessors(
        @Nonnull XsltCompilationThreads threads, @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element config
    ) throws ConfigurationException {
        var postProcessorElements = getSubElements(config, "post-process");
        var postProcessors = new ArrayList<DataSourcePostProcessor>();
        for (int i = 0; i < postProcessorElements.size(); i++) {
            try {
                postProcessors.add(new DataSourcePostProcessor(threads,
                    dataSourcePostProcessingXsltDir, postProcessorElements.get(i)));
            }
            catch (ConfigurationException e) {
                var ordinal = i==0 ? "1st" : i==1 ? "2nd" : i==2 ? "3rd" : (i+1)+"th (starting at 1)";
                throw new ConfigurationException(ordinal + " <post-process>", e);
            }
        }
        return postProcessors;
    }

    @SneakyThrows(DocumentTemplateInvalidException.class)
    public @Nonnull Element[] postProcess(@Nonnull Element[] elements) throws TransformationFailedException {
        try {
            var inputDoc = DomParser.newDocumentBuilder().newDocument();
            var inputRoot = inputDoc.createElement("data-source-post-processing-input");
            inputDoc.appendChild(inputRoot);
            for (var e : elements) inputRoot.appendChild(inputDoc.importNode(e, true));

            var output = new DOMResult();
            xslt.newTransformer().transform(new DOMSource(inputDoc), output);

            var outputDoc = (Document) output.getNode();
            if ( ! outputDoc.getDocumentElement().getNodeName().equals("data-source-post-processing-output"))
                throw new TransformationFailedException("Data Source post-processing returned a document whose root node was <" +
                    outputDoc.getDocumentElement().getNodeName() + "> but should be <data-source-post-processing-output>");
            var result = new ArrayList<Element>();
            for (int i = 0; i < outputDoc.getDocumentElement().getChildNodes().getLength(); i++) {
                var x = outputDoc.getDocumentElement().getChildNodes().item(i);
                if (x instanceof Element) result.add((Element) x);
            }
            return result.toArray(Element[]::new);
        }
        catch (TransformerException e) { throw new TransformationFailedException(e); }
    }
    
    public static @Nonnull Element[] postProcess(@Nonnull List<DataSourcePostProcessor> postProcessors, @Nonnull Element[] elements)
    throws TransformationFailedException {
        for (int i = 0; i < postProcessors.size(); i++) {
            try { elements = postProcessors.get(i).postProcess(elements); }
            catch (TransformationFailedException e) {
                var ordinal = i==0 ? "1st" : i==1 ? "2nd" : i==2 ? "3rd" : (i+1)+"th (starting at 1)";
                throw new TransformationFailedException(ordinal + " <post-process>", e);
            }
        }
        return elements;
    }
}
 