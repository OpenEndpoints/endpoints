package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.UploadedFile;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.datasource.TransformationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.util.*;

import static com.databasesandlife.util.DomParser.*;
import static com.offerready.xslt.EmailPartDocumentDestination.newMimeBodyForDestination;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.util.stream.Collectors.toList;

public class EmailTask extends Task {
    
    protected static abstract class Attachment {
        public @Nonnull TaskCondition condition;
        public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { }
        public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
    }

    protected static class AttachmentStatic extends Attachment {
        public @Nonnull File file;
    }

    protected static class AttachmentTransformation extends Attachment {
        public @Nonnull String filenamePattern;
        public @Nonnull Transformer contents;
        public @Override void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
            super.assertParametersSuffice(params);
            PlaintextParameterReplacer.assertParametersSuffice(params, filenamePattern, "filename");
            contents.assertParametersSuffice(params);
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

    @SneakyThrows(IOException.class)
    public EmailTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, httpXsltDirectory, transformers, staticDir, config);

        fromPattern = getMandatorySingleSubElement(config, "from").getTextContent().trim();
        subjectPattern = getMandatorySingleSubElement(config, "subject").getTextContent().trim();

        toPatterns = getSubElements(config, "to").stream().map(e -> e.getTextContent().trim()).collect(toList());
        if (toPatterns.isEmpty()) throw new ConfigurationException("At least one <to> must be present, " +
            "otherwise no emails would be sent, and the task would be pointless");

        for (var e : getSubElements(config, "body-transformation"))
            alternativeBodies.add(findTransformer(transformers, e));
        if (alternativeBodies.isEmpty()) throw new ConfigurationException("<body-transformation> is missing");

        for (var a : getSubElements(config, "attachment-static", "attachment-transformation",
                "attachments-from-request-file-uploads")) {
            final Attachment attachment;
            switch (a.getTagName()) {
                case "attachment-static":
                    var filename = getMandatoryAttribute(a, "filename");
                    var attachmentStatic = new AttachmentStatic();
                    attachmentStatic.file = new File(staticDir, filename);
                    if ( ! attachmentStatic.file.getCanonicalPath().startsWith(staticDir.getCanonicalPath()+File.separator))
                        throw new ConfigurationException("<attachment-static>: Filename " +
                            "'" + filename + "' attempts to reference outside application's 'static' directory");
                    if ( ! attachmentStatic.file.exists())
                        throw new ConfigurationException("<attachment-static>: Filename " +
                            "'" + filename + "' not found in application's 'static' directory");
                    attachment = attachmentStatic;
                    break;
                case "attachment-transformation":
                    var attachmentTransformation = new AttachmentTransformation();
                    attachmentTransformation.filenamePattern = getMandatoryAttribute(a, "filename");
                    attachmentTransformation.contents = findTransformer(transformers, a);
                    attachment = attachmentTransformation;
                    break;
                case "attachments-from-request-file-uploads":
                    attachment = new AttachmentsFromRequestFileUploads();
                    break;
                default:
                    throw new RuntimeException(a.getTagName());
            }
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
        for (var b : alternativeBodies) b.assertParametersSuffice(params);
        for (var a : attachments) a.assertParametersSuffice(params);
    }

    @Override
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        super.assertTemplatesValid();
        for (var b : alternativeBodies) b.assertTemplatesValid();
        for (var a : attachments) a.assertTemplatesValid();
    }
    
    @Override
    @SneakyThrows({MessagingException.class, IOException.class})
    public @Nonnull void scheduleTaskExecutionUnconditionally(@Nonnull TransformationContext context) 
    throws TaskExecutionFailedException {
        // It would be possible to do this "creation" work in a task in the ThreadPool as well, but it takes 0.000 seconds
        // by my measurement, so there's no need to introduce that complexity.

        var mainPart = new MimeMultipart("mixed");
        var partTasks = new ArrayList<Runnable>();

        Multipart body = new MimeMultipart("alternative");
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(body);
        mainPart.addBodyPart(bodyPart);
        for (var bodyTransformer : alternativeBodies) {
            try {
                var xslt = context.scheduleTransformation(bodyTransformer);
                var ourBodyPart = newMimeBodyForDestination(xslt.result);
                body.addBodyPart(ourBodyPart);
                partTasks.add(xslt);
            }
            catch (TransformationFailedException e) { throw new TaskExecutionFailedException("Email body", e); }
        }

        for (var at : attachments) {
            if ( ! at.condition.evaluate(context.params)) continue;

            if (at instanceof AttachmentStatic) {
                var attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(((AttachmentStatic) at).file);
                attachmentPart.setDisposition(Part.ATTACHMENT);
                mainPart.addBodyPart(attachmentPart);
            }
            else if (at instanceof AttachmentTransformation) {
                var a = (AttachmentTransformation) at;
                try {
                    var xslt = context.scheduleTransformation(a.contents);
                    var part = newMimeBodyForDestination(xslt.result);
                    part.setFileName(replacePlainTextParameters(a.filenamePattern, context.params));
                    part.setDisposition(Part.ATTACHMENT);
                    mainPart.addBodyPart(part);
                    partTasks.add(xslt);
                }
                catch (TransformationFailedException e) { throw new TaskExecutionFailedException("Attachment '"+a.filenamePattern+"'", e); }
            }
            else if (at instanceof AttachmentsFromRequestFileUploads) {
                for (var upload : context.fileUploads) {
                    var filePart = new MimeBodyPart();
                    filePart.setDataHandler(new DataHandler(new UploadedFileDataSource(upload)));
                    filePart.setFileName(upload.getSubmittedFileName());
                    filePart.setDisposition(Part.ATTACHMENT);
                    mainPart.addBodyPart(filePart);
                }
            }
            else throw new RuntimeException(at.getClass().getName());
        }

        context.threads.addTaskWithDependencies(partTasks, () -> {
            try {
                assert context.tx.email != null; // because requiresEmailServer() returns true
                synchronized (context.tx.email) {
                    for (var toPattern : toPatterns) {
                        var msg = context.tx.email.newMimeMessage();
                        msg.setFrom(new InternetAddress(replacePlainTextParameters(fromPattern, context.params)));
                        msg.addRecipient(RecipientType.TO, new InternetAddress(replacePlainTextParameters(toPattern, context.params)));
                        msg.setSubject(replacePlainTextParameters(subjectPattern, context.params));
                        msg.setContent(mainPart);
                        msg.setSentDate(new Date());

                        context.tx.email.send(msg);
                    }
                }
            }
            catch (MessagingException e) { throw new RuntimeException(e); }
        });
    }
}
