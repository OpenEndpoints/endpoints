package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.destination.BufferedDocumentGenerationDestination;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

public class OoxmlParameterExpander {
    
    protected final @Nonnull String filenamePattern;
    protected final @Nonnull File input;
    
    public OoxmlParameterExpander(@Nonnull File ooxmlContainerDir, @Nonnull String filenameAttribute, @Nonnull Element config)
    throws ConfigurationException {
        assertNoOtherElements(config);
        var filename = getMandatoryAttribute(config, "source");
        this.input = new File(ooxmlContainerDir, filename);
        if ( ! input.exists()) throw new ConfigurationException("File '" + filename
            + "' cannot be found in '" + ooxmlContainerDir.getName() + "' directory");

        this.filenamePattern = getMandatoryAttribute(config, filenameAttribute);
    }

    /**
     * @param output This will be closed
     */
    @SneakyThrows(IOException.class)
    protected void expand(@Nonnull OutputStream output, @Nonnull Map<String, LazyCachingValue> params) throws ConfigurationException {
        try (
            var zipInput = new ZipInputStream(new FileInputStream(input));
            var zipOutput = new ZipOutputStream(output);
            var ignored = new Timer(getClass().getSimpleName() + " expansion, input='" + input.getName() + "'")
        ) {
            while (true) {
                var entry = zipInput.getNextEntry();
                if (entry == null) break;
                try {
                    zipOutput.putNextEntry(new ZipEntry(entry.getName()));

                    if (entry.getName().endsWith("document.xml") || entry.getName().matches(".*slide\\d+\\.xml")) {
                        var xmlInput = DomParser.from(new ByteArrayInputStream(zipInput.readAllBytes()));
                        var xmlOutput = DomVariableExpander.expand(dollarThenBraces, p -> params.get(p).get(), xmlInput);
                        var xmlOutputFormatted = DomParser.formatXmlPretty(xmlOutput.getDocumentElement());
                        zipOutput.write(xmlOutputFormatted.getBytes(UTF_8));
                    } else {
                        zipOutput.write(zipInput.readAllBytes());
                    }
                }
                catch (ConfigurationException e) { throw new ConfigurationException("Entry '" + entry.getName() + "'", e); }
            }
        }
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> inputIntermediateValues
    ) throws ConfigurationException {
        var stringParams = PlaintextParameterReplacer.getKeys(params, inputIntermediateValues);
        expand(NULL_OUTPUT_STREAM, stringParams.stream().collect(toMap(p -> p, p -> LazyCachingValue.newFixed(""))));
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, filenamePattern, 
            "'download-filename' attribute");
    }

    protected @Nonnull String getContentTypeForFilename(@Nonnull String filename) {
        if (filename.toLowerCase().endsWith(".docx"))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if (filename.toLowerCase().endsWith(".xlsx"))
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        else if (filename.toLowerCase().endsWith(".pptx"))
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        else return "application/octet-stream";
    }

    public @Nonnull Runnable scheduleExecution(
        @Nonnull TransformationContext context, 
        @Nonnull BufferedDocumentGenerationDestination destination,
        @Nonnull Set<IntermediateValueName> inputIntermediateValues
    ) {
        return context.threads.addTask(() -> {
            try {
                var stringParams = context.getParametersAndIntermediateValuesAndSecrets(inputIntermediateValues);
                var filename = replacePlainTextParameters(filenamePattern, stringParams);
                
                destination.setContentDispositionToDownload(filename);
                destination.setContentType(getContentTypeForFilename(filename));

                expand(destination.getOutputStream(), stringParams);
            }
            catch (ConfigurationException e) { throw new RuntimeException(e); }
        });
    }
}
