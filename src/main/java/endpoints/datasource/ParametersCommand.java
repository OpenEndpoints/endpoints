package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.UploadedFile;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParametersCommand extends DataSourceCommand {

    public ParametersCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, config);
    }

    public static @Nonnull Element[] createParametersElements(
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads
    ) {
        var doc = DomParser.newDocumentBuilder().newDocument();
        var result = new ArrayList<Element>();
        for (var p : params.entrySet()) {
            var el = doc.createElement("parameter");
            el.setAttribute("name", p.getKey().name);
            el.setAttribute("value", p.getValue());
            result.add(el);
        }
        for (var f : fileUploads) {
            var el = doc.createElement("file-upload");
            el.setAttribute("field-name", f.getFieldName());
            el.setAttribute("upload-filename", f.getSubmittedFileName());
            if (f.getXmlDocumentOrNull() != null) el.appendChild(doc.importNode(f.getXmlDocumentOrNull(), true));
            result.add(el);
        }
        return result.toArray(new Element[0]);
    }

    public static @Nonnull Element createParametersElement(
        @Nonnull String tagName, 
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads
    ) {
        var doc = DomParser.newDocumentBuilder().newDocument();
        var result = doc.createElement(tagName);
        doc.appendChild(result);
        for (var e : createParametersElements(params, fileUploads))
            result.appendChild(doc.importNode(e, true));
        return result;
    }

    @Override
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                return new Element[] {
                    createParametersElement("parameters", context.params, context.fileUploads)
                };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
