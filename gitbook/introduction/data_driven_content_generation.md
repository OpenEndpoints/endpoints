## "Placeholder Document Template" vs "Data Driven Content Template"
Typical solutions for **automated document generation** use templates in which the content itself actually is part of the template, while external content is merged with such template by inserting it into placeholders.

This type of document template still has advantages in many use cases, but it also has limitations and disadvantages:

- Each time the content has changed, the template itself must also be adjusted. Thus, for example, the simple task of correcting a spelling error cannot be done by "anyone", but only by someone who does not "destroy" the template.
- For a flexible layout that requires different text lengths, different sources and sometimes even different content structures in individual cases, this concept is technically limited. A single text that is too long can then already cause an unintended page break that ruins the whole layout ...
- Such concept is not really suitable for content available in different languages. In fact, you will have to create a separate template for each language.

OpenEndpoints of course also supports the use of such simple placeholder templates. But beyond that we enable the use of smart "data-driven" templates, where the content is separated from the template, and where the layout can be completely controlled by the content. This is called a **data-driven content template**. Advantages of this concept include: 

- Reusability of templates for different content
- Easy maintenance of content
- Unlimited flexibility in adapting content structure, style and layout to different products, audiences, etc.
- The output is not a proprietary format (for example with macros or field functions), but a straight and clean content as it could have been created "manually".
- Content Types are not limited to classic paged media or html, but can even generate images from content, or for example transform a content from a CMS into a specific JSON payload.

## Technology: XSLT
Content is loaded from various sources (including the payload from calling the endpoint) and (if not already in XML) automatically converted to XML.

The transformation is then done via XSLT. 100% W3C standard. Old, but 
- stable and well documented.
- easy to test.
- still without alternative when it comes to **professional** content transformation.

## SchemaBites
On our website https://openendpoints.io we promote the idea of importing content from different sources (by means of conversion) into unified schemes, for example those promoted by https://schema.org/ schemes.

As a consequence, XSLT templates from different projects can be applied to all data using the same schema. Thus, we create a community for the creation and exchange of "smart" content-driven templates.

We refer to these universal smart templates as **SchemaBites** on https//:openendpoints.io.  In fact, these are not proprietary templates, but rather standard XSLT templates applied to standardized schemas.