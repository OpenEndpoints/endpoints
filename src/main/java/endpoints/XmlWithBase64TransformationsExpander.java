package endpoints;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.IdentityForwardingSaxHandler;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.destination.BufferedDocumentGenerationDestination;
import endpoints.config.IntermediateValueName;
import endpoints.config.Transformer;
import endpoints.datasource.TransformationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.transform.TransformerException;
import java.util.*;
import java.util.function.Consumer;

/** Takes XML with &lt;foo xslt-transformation="foo" encoding="base64"&gt; tags and expands them */
@RequiredArgsConstructor
public class XmlWithBase64TransformationsExpander {
    
    protected @Nonnull TransformationContext context;
    protected @Nonnull Document input;
    protected @Nonnull Map<Transformer, BufferedDocumentGenerationDestination> transformerOutput = new IdentityHashMap<>();
    
    @SneakyThrows(TransformerException.class)
    protected Map<String, Transformer> parseTransformers() {
        var result = new HashMap<String, Transformer>();
        DomVariableExpander.expand(input, x -> new IdentityForwardingSaxHandler(x) {
            @SneakyThrows(ConfigurationException.class)
            @Override public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                var transformerName = atts.getValue("xslt-transformation");
                if (transformerName != null) {
                    var encoding = atts.getValue("encoding");
                    if ( ! "base64".equals(encoding)) throw new ConfigurationException("<"+localName+" xslt-transformation='"
                        + transformerName + "' encoding='"+encoding+"'> must have encoding='base64'");
                    
                    var transformer = context.application.getTransformers().get(transformerName);
                    if (transformer == null) throw new ConfigurationException("<"+localName+" xslt-transformation='"
                        + transformerName + "'>: Transformer '" + transformerName + "' not found in application");
                    result.put(transformerName, transformer);
                }

                super.startElement(uri, localName, qName, atts);
            }
        });
        return result;
    }

    @SneakyThrows(TransformerException.class)
    protected @Nonnull Document insertFilesIntoXml() {
        return DomVariableExpander.expand(input, x -> new IdentityForwardingSaxHandler(x) {
            @Override public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                var transformerName = atts.getValue("xslt-transformation");
                if (transformerName != null) {
                    var transformer = context.application.getTransformers().get(transformerName);
                    if (transformer == null) throw new RuntimeException(transformerName);
                    var bytes = transformerOutput.get(transformer).getBody().toByteArray();

                    super.startElement(uri, localName, qName, atts);

                    var base64 = Base64.encodeBase64String(bytes).toCharArray();
                    super.characters(base64, 0, base64.length);
                } else {
                    super.startElement(uri, localName, qName, atts);
                }
            }
        });
    }

    public @Nonnull Runnable schedule(
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull Consumer<Document> after
    ) throws TransformationFailedException {
        var transformers = parseTransformers();
        
        var futures = new ArrayList<Runnable>(transformers.size());
        for (var t : transformers.values()) {
            var destination = new BufferedDocumentGenerationDestination();
            var transformTask = context.scheduleTransformation(destination, t, visibleIntermediateValues);
            transformerOutput.put(t, destination);
            futures.add(transformTask);
        }
        
        Runnable placeBase64InXml = () -> after.accept(insertFilesIntoXml());
        context.threads.addTaskWithDependencies(futures, placeBase64InXml);
        
        return placeBase64InXml;
    }
}
