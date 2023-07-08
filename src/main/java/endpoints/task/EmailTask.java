package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import com.offerready.xslt.destination.EmailPartDocumentDestination;
import endpoints.OoxmlParameterExpander;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.UploadedFile;
import endpoints.condition.Condition;
import endpoints.config.*;
import endpoints.config.response.StaticResponseConfiguration;
import endpoints.datasource.TransformationFailedException;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.mail.BodyPart;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static com.offerready.xslt.destination.EmailPartDocumentDestination.newMimeBodyForDestination;
import static endpoints.config.ApplicationFactory.ooxmlResponsesDir;
import static java.nio.charset.StandardCharsets.UTF_8;

public class EmailTask extends Task {
    
    protected static abstract class Attachment {
        public @Nonnull Condition condition;
        
        public void assertParametersSuffice(
            @Nonnull Set<ParameterName> params,
            @Nonnull Set<IntermediateValueName> visibleIntermediateValues
        ) throws ConfigurationException { }
        
        public void assertTemplatesValid() 
        throws DocumentTemplateInvalidException { }
    }

    protected static class AttachmentStatic extends Attachment {
        public @Nonnull File file;
    }

    protected static class AttachmentTransformation extends Attachment {
        public @Nonnull String filenamePattern;
        public @Nonnull Transformer contents;
        
        @Override public void assertParametersSuffice(
            @Nonnull Set<ParameterName> params,
            @Nonnull Set<IntermediateValueName> visibleIntermediateValues
        ) throws ConfigurationException {
            super.assertParametersSuffice(params, visibleIntermediateValues);
            PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues, filenamePattern, "filename");
            contents.assertParametersSuffice(params, visibleIntermediateValues);
        }
        
        @Override public void assertTemplatesValid() throws DocumentTemplateInvalidException {
            super.assertTemplatesValid();
            contents.assertTemplatesValid();
        }
    }
    
    protected static class AttachmentOoxmlParameterExpansion extends Attachment {
        protected @Nonnull OoxmlParameterExpander expander;
        
        @Override public void assertParametersSuffice(
            @Nonnull Set<ParameterName> params,
            @Nonnull Set<IntermediateValueName> visibleIntermediateValues
        ) throws ConfigurationException {
            expander.assertParametersSuffice(params, visibleIntermediateValues);
        }
    }

    protected static class AttachmentsFromRequestFileUploads extends Attachment {
    }

    protected record UploadedFileDataSource(
        UploadedFile part
    ) implements DataSource {
        @Override public String getContentType() { synchronized (part) { return part.getContentType(); } }
        @Override public InputStream getInputStream() { synchronized (part) { return part.getInputStream(); } }
        @Override public @CheckForNull String getName() { synchronized (part) { return part.getSubmittedFileName(); } }
        @Override public OutputStream getOutputStream() { throw new RuntimeException("unreachable"); }
    }

    protected final @Nonnull File staticDir;
    protected final @Nonnull String fromPattern, subjectPattern;
    protected final @Nonnull List<String> toPatterns;
    protected final @Nonnull List<Transformer> alternativeBodies = new ArrayList<>();
    protected final @Nonnull List<Attachment> attachments = new ArrayList<>();
    
    protected @Nonnull Transformer findTransformer(@Nonnull Map<String, Transformer> transformers, @Nonnull Element el)
    throws ConfigurationException {
        var name = getMandatoryAttribute(el, "name");
        if ( ! transformers.containsKey(name)) throw new ConfigurationException("<"+el.getTagName()+
            ">: Referenced transformation '"+name+"' not found");
        return transformers.get(name);
    }

    public @Nonnull AttachmentStatic findStaticFileAndAssertExists(@Nonnull String errorPrefix, @Nonnull String filename)
    throws ConfigurationException {
        try {
            var result = new AttachmentStatic();
            result.file = StaticResponseConfiguration.findStaticFileAndAssertExists(staticDir, filename);
            return result;
        }
        catch (ConfigurationException e) { throw new ConfigurationException(errorPrefix, e); }
    }

    public EmailTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Map<String, Transformer> transformers,
        int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, transformers, indexFromZero, config);
        
        assertNoOtherElements(config, "after", "input-intermediate-value","from", "to", "subject", "body-transformation",
            "attachment-static", "attachment-transformation", "attachment-ooxml-parameter-expansion", 
            "attachments-from-request-file-uploads");

        this.staticDir = new File(applicationDir, ApplicationFactory.staticDir);

        fromPattern = getMandatorySingleSubElement(config, "from").getTextContent().trim();
        subjectPattern = getMandatorySingleSubElement(config, "subject").getTextContent().trim();

        toPatterns = getSubElements(config, "to").stream().map(e -> e.getTextContent().trim()).toList();
        if (toPatterns.isEmpty()) throw new ConfigurationException("At least one <to> must be present, " +
            "otherwise no emails would be sent, and the task would be pointless");

        for (var e : getSubElements(config, "body-transformation"))
            alternativeBodies.add(findTransformer(transformers, e));
        if (alternativeBodies.isEmpty()) throw new ConfigurationException("<body-transformation> is missing");

        for (var a : getSubElements(config, "attachment-static", "attachment-transformation",
                "attachment-ooxml-parameter-expansion", "attachments-from-request-file-uploads")) {
            var attachment = switch (a.getTagName()) {
                case "attachment-static" -> 
                    findStaticFileAndAssertExists("<attachment-static>", 
                        getMandatoryAttribute(a, "filename"));
                case "attachment-transformation" -> {
                    var result = new AttachmentTransformation();
                    result.filenamePattern = getMandatoryAttribute(a, "filename");
                    result.contents = findTransformer(transformers, a);
                    yield result;
                }
                case "attachment-ooxml-parameter-expansion" -> {
                    var result = new AttachmentOoxmlParameterExpansion();
                    result.expander = new OoxmlParameterExpander(new File(applicationDir, ooxmlResponsesDir), "filename", a);
                    yield result;
                }
                case "attachments-from-request-file-uploads" -> new AttachmentsFromRequestFileUploads();
                default -> throw new RuntimeException(a.getTagName());
            };
            attachment.condition = new Condition(a);
            this.attachments.add(attachment);
        }
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, fromPattern, "<from>");
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, subjectPattern, "<subject>");
        for (var t : toPatterns) 
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, t, "<to>");
        for (var b : alternativeBodies) b.assertParametersSuffice(params, inputIntermediateValues);
        for (var a : attachments) a.assertParametersSuffice(params, inputIntermediateValues);
    }

    @Override
    public void assertCompatibleWithEmailConfig(
        @CheckForNull EmailSendingConfigurationFactory config, @Nonnull Set<ParameterName> params
    ) throws ConfigurationException {
        if (config == null) throw new ConfigurationException("'email-sending-configuration.xml' missing");
        config.assertParametersSuffice(params, inputIntermediateValues);
    }

    @Override
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        super.assertTemplatesValid();
        for (var b : alternativeBodies) b.assertTemplatesValid();
        for (var a : attachments) a.assertTemplatesValid();
    }

    /** Takes HTML which has been previously produced, and replaces cid:xx images with correct references, and adds attachments */ 
    @SneakyThrows(MessagingException.class)
    protected @Nonnull MimeBodyPart scheduleHtmlBodyPart(
        @Nonnull TransformationContext context, @Nonnull BodyPart html, 
        @Nonnull List<Runnable> partTasks, @Nonnull Runnable htmlExecutor, @Nonnull EmailPartDocumentDestination destination
    ) {
        var bodyPart = new MimeBodyPart();
        var body = new MimeMultipart("related");
        bodyPart.setContent(body);

        body.addBodyPart(html);

        var task = new Runnable() {
            @SneakyThrows({MessagingException.class, TaskExecutionFailedException.class})
            @Override public void run() {
                var referencedFiles = new TreeMap<String, BodyPart>();
                var matcher = Pattern.compile("['\"]cid:([/\\w\\-]+\\.\\w{3,4})['\"]")
                    .matcher(destination.getBody().toString(UTF_8));
                while (matcher.find()) {
                    var whole = matcher.group();
                    var path = matcher.group(1); // e.g. "foo/bar.jpg"
                    
                    final AttachmentStatic staticFile;
                    try { staticFile = findStaticFileAndAssertExists(whole, path); }
                    catch (ConfigurationException e) { throw new TaskExecutionFailedException(e); }

                    var filePart = new MimeBodyPart();
                    var source = new DataSource() {
                        public String getContentType() { return new MimetypesFileTypeMap().getContentType(path); }
                        public String getName() { return path; }
                        public OutputStream getOutputStream() { throw new RuntimeException(); }
                        
                        @SneakyThrows({FileNotFoundException.class})
                        public InputStream getInputStream() { 
                            return new FileInputStream(staticFile.file); 
                        }
                    };
                    filePart.setDataHandler(new DataHandler(source));
                    filePart.setFileName(path);
                    filePart.setHeader("Content-ID", "<"+path+">");
                    filePart.setDisposition(Part.INLINE);
    
                    referencedFiles.put(path, filePart);
                }
    
                for (var attachmentPart : referencedFiles.values())
                    body.addBodyPart(attachmentPart);
            }
        };
        
        context.threads.addTaskWithDependencies(List.of(htmlExecutor), task);
        partTasks.add(task);
        
        return bodyPart;
    }
    
    @Override
    @SneakyThrows({MessagingException.class, IOException.class})
    public void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    ) throws TaskExecutionFailedException {
        var stringParams = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);

        var mainPart = new MimeMultipart("mixed");
        var partTasks = new ArrayList<Runnable>();

        var bodyPart = new MimeBodyPart();
        var body = new MimeMultipart("alternative");
        bodyPart.setContent(body);
        mainPart.addBodyPart(bodyPart);
        for (var bodyTransformer : alternativeBodies) {
            try {
                var bodyDestination = new EmailPartDocumentDestination();
                var xslt = context.scheduleTransformation(bodyDestination, bodyTransformer, inputIntermediateValues);
                var contentsBodyPart = newMimeBodyForDestination(bodyDestination);
                var contentType = Optional.ofNullable(bodyTransformer.getDefn().contentType).orElse("").toLowerCase();
                if (contentType.contains("text/html") && contentType.contains("utf-8")) {
                    // This adds its final task, which depends on "xslt", to "partTasks"
                    body.addBodyPart(scheduleHtmlBodyPart(context, contentsBodyPart, partTasks, xslt, bodyDestination));
                } else {
                    partTasks.add(xslt);
                    body.addBodyPart(contentsBodyPart);
                }
            }
            catch (TransformationFailedException e) { throw new TaskExecutionFailedException("Email body", e); }
        }

        for (var at : attachments) {
            if ( ! at.condition.evaluate(context.endpoint.getParameterMultipleValueSeparator(), stringParams)) continue;
            
            if (at instanceof AttachmentStatic s) {
                var attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(s.file);
                attachmentPart.setDisposition(Part.ATTACHMENT);
                mainPart.addBodyPart(attachmentPart);
            }
            else if (at instanceof AttachmentTransformation a) {
                try {
                    var result = new EmailPartDocumentDestination();
                    result.setContentDispositionToDownload(replacePlainTextParameters(a.filenamePattern, stringParams));
                    partTasks.add(context.scheduleTransformation(result, a.contents, inputIntermediateValues));
                    mainPart.addBodyPart(result.getBodyPart());
                }
                catch (TransformationFailedException e) { throw new TaskExecutionFailedException("Attachment '"+a.filenamePattern+"'", e); }
            }
            else if (at instanceof AttachmentOoxmlParameterExpansion o) {
                var result = new EmailPartDocumentDestination();
                partTasks.add(o.expander.scheduleExecution(context, result, inputIntermediateValues));
                mainPart.addBodyPart(result.getBodyPart());
            }
            else if (at instanceof AttachmentsFromRequestFileUploads) {
                for (var upload : context.request.getUploadedFiles()) {
                    var filePart = new MimeBodyPart();
                    filePart.setDataHandler(new DataHandler(new UploadedFileDataSource(upload)));
                    filePart.setFileName(upload.getSubmittedFileName());
                    filePart.setDisposition(Part.ATTACHMENT);
                    mainPart.addBodyPart(filePart);
                }
            }
            else throw new RuntimeException(at.getClass().getName());
        }

        Runnable sendEmail = () -> {
            try {
                var emailTransaction = context.tx.getEmailTransaction(context, inputIntermediateValues);
                synchronized (emailTransaction) {
                    for (var toPattern : toPatterns) {
                        var msg = emailTransaction.newMimeMessage();
                        msg.setFrom(new InternetAddress(replacePlainTextParameters(fromPattern, stringParams)));
                        msg.addRecipient(RecipientType.TO, new InternetAddress(replacePlainTextParameters(toPattern, stringParams)));
                        msg.setSubject(replacePlainTextParameters(subjectPattern, stringParams));
                        msg.setContent(mainPart);
                        msg.setSentDate(new Date());

                        emailTransaction.send(msg);
                    }
                }
            }
            catch (ConfigurationException | MessagingException e) { throw new RuntimeException(e); }
        };
        context.threads.addTaskWithDependencies(partTasks, sendEmail);
        
        context.threads.addTaskWithDependencies(List.of(sendEmail), workComplete);
    }
}
