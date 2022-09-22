# Email Task

To send an email from OpenEndpoints you need to

1. Configure your email server
2. Create an email task

## Configure Email Server

To send email from your application the file "email-sending-configuration.xml" must be present under application.

```xml
<email-sending-configuration>
    <server>hostname-to-connect-to</server>
    <!--optional-->
    <port>xxxx</port>
    <!--optional-->
    <username>xxxx</username>
    <password>xxxx</password>
    <!--optional: zero or many -->
    <header name='foo'>some-value</header>
</email-sending-configuration>
```

The file has the root element \<email-sending-configuration> and have the following sets of sub-elements.

* If no username and password are set, TLS will not be used
* extra headers are written into every email sent via SMTP, for example authorization headers for a commercial email sending service

### Alternative Set-Up:

An alternative option is to configure an MX address for the DNS lookup.

```xml
<email-sending-configuration>
    <mx-address>an address for MX DNS lookup.</mx-address>
</email-sending-configuration>
```

## Create Email Task

The task \<task class="endpoints.task.EmailTask"> sends an email. It has the following sub-elements configuring it:

```xml
<task class="endpoints.task.EmailTask">
    <from>me@my-email.com</from>
    <to>${client-email}</to>
    <subject>Message from OpenEndpoints</subject>
    <!-- one or many; "many" will create an multipart/alternative email part -->
    <body-transformation name="a-transformation-generating-text-or-html"/>
    <!-- zero or many -->
    <attachment-static filename="path-to-file-in-static"/>
    <!-- zero or many -->
    <attachment-transformation name="a-transformation" filename="invoice-${invoice-number}.pdf"/>
    <!-- zero or many -->
    <attachment-ooxml-parameter-expansion source="foo.docx" filename="invoice-${invoice-number}.pdf"/>
    <!-- zero or one -->
    <attachments-from-request-file-uploads/>
</task>
```

• \<from> is mandatory (variables are expanded)

• \<to> is mandatory (variables are expanded). There may be multiple \<to> elements. Each \<to> sends a separate email, to just this recipient. Per \<to>, only one recipient address is allowed

• \<subject> is mandatory (variables are expanded)

• \<body-transformation name="a-transformation"/> is mandatory, and can appear multiple times. This references a transformation (see below). All the different results are placed into a ”multipart/alternative” email part. It would be normal for one referenced transformation to produce HTML and the other plain text.

• \<attachment-static filename="path/foo.pdf"> takes the foo.pdf file out of the static directory and includes it as an attachment in the email. Variables are not allowed in the filename attribute.

• \<attachment-transformation name="a-transformation" filename="invoice-${invoice-number}.pdf"/>. For each of the elements, the transformation is executed, and the resulting bytes are attached as a file to the sent email. The name of the file is specified in the filename attribute, variables are expanded.

• \<attachment-ooxml-parameter-expansion source="foo.docx" filename="invoice-${invoice-number}.pdf"/> will read in the file “foo.docx” from the “ooxml-responses” directory under the Endpoint's configuration and replace any ${foo} variables in the document's body, and deliver it. Only DOCX is supported; DOC is not supported. The name of the file is specified in the filename attribute, parameters like ${foo} are expanded.

• \<attachments-from-request-file-uploads/>. This includes as attachments all file uploads that have been uploaded to this request. Any attachment may (optionally) have attributes such as if="${foo}" equals="bar".

### Embedding Images

If the body has a content type like text/html; charset=utf-8 then it may include tags such as \<img src="cid:foo/bar.jpg">. The tag is most commonly an \<img> but can be any tag.

The system then searches in the static directory for any file with that path. The file is included with the image, as a “related” multi-part part, meaning the file is available to the HTML document when its rendered in the email client.
