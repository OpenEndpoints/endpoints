## Best of Breed - JSON and XML
There are sometimes debates whether XML is an outdated technology that has long since been replaced by JSON, for example. We don't see it that way! We think that there is no contradiction in using both technologies - because both have different strengths and weaknesses.

OpenEndpoints is perfect for a "best of breed" of both technologies.

## Why XML And Not JSON?

JSON has largely replaced XML as the data format for describing and passing structured data. But that doesn't mean that XML no longer has any merits. While there are certainly good reasons why JSON is preferred over XML in many application areas, the great strength of XML technology lies in its **comprehensive support for data transformation**.

XSL Transformation, or XSLT for short, is the perfect technology for converting structured data and creating PDF, HTML or other content from it (see: [https://en.wikipedia.org/wiki/XSLT](https://en.wikipedia.org/wiki/XSLT)). However, an XSLT processor is required for this, which is a hurdle for many projects.

OpenEndpoints solves this problem. The content transformation, which uses the full capabilities of XSLT, can be seamlessly integrated into any other stack, having the XML technology encapsulated into the OpenEndpoints Docker container. 

- JSON as a data-source will be automatically converted into XML so that XSLT can be used with the integrated XSLT processor.
- Any XML output can be automatically converted to JSON.
- XSLT of course also can output JSON.

![input json - processing xml - output json](https://cdn.openendpoints.io/images/gitbook/introduction-json-xml-json.svg)

## Why XSLT and not something else?

Because there is no adequate alternative to it.

We believe there is no need to reinvent the wheel: XSLT is a very widespread, stable and mature standard that is also used in many commercial projects. There is no equivalent alternative, especially when it comes to generate paged media.