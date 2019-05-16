package endpoints.serviceportal.wicket.endpointmenu;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.UploadedFile;
import endpoints.PublishEnvironment;
import endpoints.config.ParameterName;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointFormMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.fileupload.FileItem;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.protocol.http.servlet.MultipartServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.wicket.AttributeModifier.append;

public class EndpointFormPage extends EndpointPage {
    
    protected final @Nonnull EndpointPanel results;

    @RequiredArgsConstructor
    protected static class WicketUploadedFile extends UploadedFile {
        final protected @Nonnull FileItem item;

        @Override public @Nonnull String getFieldName() { return item.getFieldName(); }
        @Override public @Nonnull String getContentType() { return item.getContentType(); }
        @SneakyThrows(IOException.class)
        @Override public @Nonnull InputStream getInputStream() { return item.getInputStream(); }
        @Override public @CheckForNull String getSubmittedFileName() {
            if ("".equals(item.getName())) throw new RuntimeException("Filename for file upload field '"
                + getFieldName() + "' may be missing or present, but may not be empty");
            return item.getName();
        }
    }

    public EndpointFormPage(@Nonnull PublishEnvironment environment, @Nonnull MultiEnvironmentEndpointLeafMenuItem item) {
        super(environment, item);

        try (DbTransaction tx = DeploymentParameters.get().newDbTransaction()) {
            var i = (ServicePortalEndpointFormMenuItem) item.itemForEnvironment.get(environment);

            results = new EndpointPanel("results", environment, i.result);
            results.setOutputMarkupId(true);
            add(results);

            var form = new Form<Void>("form");
            form.setMultiPart(true);
            form.add(new EndpointPanel("formContents", environment, i.form).execute(tx));
            form.add(new AjaxSubmitLink("submit", form) {
                @Override protected void onSubmit(@Nonnull AjaxRequestTarget target) {
                    EndpointFormPage.this.onSubmit((MultipartServletWebRequest) RequestCycle.get().getRequest());
                    target.add(results);
                    
                    // It would be possible to do this Javascript line in Java by redrawing the tab with a new class
                    // However, if you submit the form that works, but if you go back to the 1st tab and submit the form 
                    // again then the second tab doesn't get selected a second time.
                    target.appendJavaScript("$('#responseTab').attr('class', '');");
                    
                    target.appendJavaScript("UIkit.switcher('#form-tab-navigation').show(1);");
                }
            });
            add(form);
        }
    }

    protected void onSubmit(@Nonnull MultipartServletWebRequest request) {
        try (DbTransaction tx = DeploymentParameters.get().newDbTransaction()) {
            var params = request.getPostParameters().getParameterNames().stream().collect(toMap(
                p -> new ParameterName(p),
                p -> request.getPostParameters().getParameterValues(p).stream().map(v -> v.toString("")).collect(toList())
            ));
            var uploadedFiles = request.getFiles().values().stream().flatMap(x -> x.stream())
                .map(item -> new WicketUploadedFile(item)).collect(toList());
            results.execute(tx, params, uploadedFiles);
            tx.commit();
        }
    }
}
