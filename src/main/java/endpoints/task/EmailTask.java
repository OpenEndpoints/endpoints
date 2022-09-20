package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.TransformationContext.TransformerExecutor;
import endpoints.UploadedFile;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.config.response.StaticResponseConfiguration;
import endpoints.datasource.TransformationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimetypesFileTypeMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static com.offerready.xslt.destination.EmailPartDocumentDestination.newMimeBodyForDestination;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

public class EmailTask extends Task {
    
    protected static abstract class Attachment {
        public @Nonnull TaskCondition condition;
        
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
        
        public @Override void assertParametersSuffice(
            @Nonnull Set<ParameterName> params,
            @Nonnull Set<IntermediateValueName> visibleIntermediateValues
        ) throws ConfigurationException {
            super.assertParametersSuffice(params, visibleIntermediateValues);
            PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues, filenamePattern, "filename");
            contents.assertParametersSuffice(params, visibleIntermediateValues);
        }
        
        public @Override void assertTemplatesValid() throws DocumentTemplateInvalidException {
            super.assertTemplatesValid();
            contents.assertTemplatesValid();
        }
    }

    protected static class AttachmentsFromRequestFileUploads extends Attachment {
    }

    @RequiredArgsConstructor
    protected static class UploadedFileDataSource implements DataSource {
        protected final UploadedFile part;

        @Override public String getContentType() {
            synchronized (part) { return part.getContentType(); }
        }
        @Override public InputStream getInputStream() { synchronized (part) { return part.getInputStream(); } }
        @Override public @CheckForNull String getName() {
            synchronized (part) { return part.getSubmittedFileName(); }
        }
        @Override public OutputStream getOutputStream() {
            throw new RuntimeException("unreachable");
        }
    };

    protected @Nonnull File staticDir;
    protected @Nonnull String fromPattern, subjectPattern;
    protected @Nonnull List<String> toPatterns;
    protected @Nonnull List<Transformer> alternativeBodies = new ArrayList<>();
    protected @Nonnull List<Attachment> attachments = new ArrayList<>();
    
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
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir,
        int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, httpXsltDirectory, transformers, staticDir, indexFromZero, config);
        
        assertNoOtherElements(config, "after", "input-intermediate-value","from", "to", "subject", "body-transformation",
            "attachment-static", "attachment-transformation", "attachments-from-request-file-uploads");

        this.staticDir = staticDir;

        fromPattern = getMandatorySingleSubElement(config, "from").getTextContent().trim();
        subjectPattern = getMandatorySingleSubElement(config, "subject").getTextContent().trim();

        toPatterns = getSubElements(config, "to").stream().map(e -> e.getTextContent().trim()).toList();
        if (toPatterns.isEmpty()) throw new ConfigurationException("At least one <to> must be present, " +
            "otherwise no emails would be sent, and the task would be pointless");

        for (var e : getSubElements(config, "body-transformation"))
            alternativeBodies.add(findTransformer(transformers, e));
        if (alternativeBodies.isEmpty()) throw new ConfigurationException("<body-transformation> is missing");

        for (var a : getSubElements(config, "attachment-static", "attachment-transformation",
                "attachments-from-request-file-uploads")) {
            var attachment = switch (a.getTagName()) {
                case "attachment-static" -> 
                    findStaticFileAndAssertExists("<attachment-static>", 
                        getMandatoryAttribute(a, "filename"));
                case "attachment-transformation" -> {
                    var attachmentTransformation = new AttachmentTransformation();
                    attachmentTransformation.filenamePattern = getMandatoryAttribute(a, "filename");
                    attachmentTransformation.contents = findTransformer(transformers, a);
                    yield attachmentTransformation;
                }
                case "attachments-from-request-file-uploads" -> new AttachmentsFromRequestFileUploads();
                default -> throw new RuntimeException(a.getTagName());
            };
            attachment.condition = new TaskCondition(a);
            this.attachments.add(attachment);
        }
    }

    @Override
    public boolean requiresEmailServer() {
        return true;
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
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        super.assertTemplatesValid();
        for (var b : alternativeBodies) b.assertTemplatesValid();
        for (var a : attachments) a.assertTemplatesValid();
    }

    @SneakyThrows(MessagingException.class)
    protected @Nonnull MimeBodyPart scheduleHtmlBodyPart(
        @Nonnull TransformationContext context, @Nonnull BodyPart html, 
        @Nonnull List<Runnable> partTasks, @Nonnull TransformerExecutor htmlExecutor
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
                    .matcher(htmlExecutor.result.getBody().toString(UTF_8));
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
                var xslt = context.scheduleTransformation(bodyTransformer, inputIntermediateValues);
                var contentsBodyPart = newMimeBodyForDestination(xslt.result);
                var contentType = Optional.ofNullable(bodyTransformer.getDefn().contentType).orElse("").toLowerCase();
                if (contentType.contains("text/html") && contentType.contains("utf-8")) {
                    body.addBodyPart(scheduleHtmlBodyPart(context, contentsBodyPart, partTasks, xslt));
                } else {
                    body.addBodyPart(contentsBodyPart);
                    partTasks.add(xslt);
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
                    var xslt = context.scheduleTransformation(a.contents, inputIntermediateValues);
                    var part = newMimeBodyForDestination(xslt.result);
                    part.setFileName(replacePlainTextParameters(a.filenamePattern, stringParams));
                    part.setDisposition(Part.ATTACHMENT);
                    mainPart.addBodyPart(part);
                    partTasks.add(xslt);
                }
                catch (TransformationFailedException e) { throw new TaskExecutionFailedException("Attachment '"+a.filenamePattern+"'", e); }
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
                assert context.tx.email != null; // because requiresEmailServer() returns true
                synchronized (context.tx.email) {
                    for (var toPattern : toPatterns) {
                        var msg = context.tx.email.newMimeMessage();
                        msg.setFrom(new InternetAddress(replacePlainTextParameters(fromPattern, stringParams)));
                        msg.addRecipient(RecipientType.TO, new InternetAddress(replacePlainTextParameters(toPattern, stringParams)));
                        msg.setSubject(replacePlainTextParameters(subjectPattern, stringParams));
                        msg.setContent(mainPart);
                        msg.setSentDate(new Date());

                        context.tx.email.send(msg);
                    }
                }
            }
            catch (MessagingException e) { throw new RuntimeException(e); }
        };
        context.threads.addTaskWithDependencies(partTasks, sendEmail);
        
        context.threads.addTaskWithDependencies(List.of(sendEmail), workComplete);
    }
}
