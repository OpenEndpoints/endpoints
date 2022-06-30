# Output Generated Content
## Return Generated Content With Request Response
In the simplest case, a generated content is returned directly as a response object of the endpoint. The request (with or without payload) is processed, a content is generated, and this content is returned directly.

When called via a web browser, the Content-Type header (set via the configuration) decides whether the content is displayed directly or a download is started. In the latter case, the file name can be set flexibly.

## Sending Emails With Attachments
OpenEndpoints can send an email, where
- the email body is generated via XSLT (and therefore there are no limits for custom layout and content).
- an unlimited number of generated documents can be attached.
- of course "static" documents, documents loaded from URL or even documents uploaded with the request can be attached to the email as well.

## Generate Payload to Interact With External APIs

Payloads required to interact with external APIs (REST, SOAP) can be dynamically generated from data-sources. This offers unlimited possibilities to dynamically adapt the content and structure of payloads to the respective requirements.
Created content can be part of this payload, provided that this content is base64 encoded. 

## Create A Shortlink
OpenEndpoints can translate a complete request into a short link. For example, your web form will send a reply email to your customer with a download link that will generate a specific document for your customer. In fact, the short link points to a different endpoint, preserving all the parameters from the original call. There are no limits to your creativity.