## Building APIs for Content Transformation

The core feature of OpenEndpoints is **loading and combining structured data from different data sources** and creating something new from this data, which can then be delivered via the respective endpoint.

A wide variety of data sources are supported, the content of which can be transformed on-the-fly to everything supported by XSLT and XSL-FO. This includes XML, PDF, HTML, JSON, SVG, RTF, and any kind of text.

![](https://cdn.openendpoints.io/images/gitbook/introduction-content-transformation-api.svg)

## Easy Deployment

OpenEndpoints can be installed quickly and easily with a Docker container. With a single installation, multiple applications can be created and operated in parallel, each of which can contain multiple endpoints.

## API First

OpenEndpoints has - with the exception of a small admin tool ("Service Portal") - no GUI. The use of OpenEndpoints expects, for example, independent web applications or simple web forms to send a request to a REST API created by OpenEndpoints.