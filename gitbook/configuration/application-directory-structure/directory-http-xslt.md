# Directory: http-xslt

OpenEndpoints can interact with third party REST or SOAP APIs, either using such API as a data-source, or to execute a TASK.

The request body of such request can be **either inline content** (=directly written into endpoints.xml or into the data-source definition), **or** the body is **generated by XSLT transformation**.

This **optional** directory contains custom xslt to generate such request bodies.

The transformation input (=the XML generated by the software, which the XSLT is applied to) does not support arbitrary data-sources, but is limited to parameter values:

![directory http xslt](https://cdn.openendpoints.io/images/gitbook/directory-http-xslt.png)

The **values** of the parameters inside that generated xml are different depending on the context:

## Request Body Within Parameter Transformation

The input xml contains the parameters from the original request. Note that the original request could have different parameters than those declared in the endpoints.xml - which is possible when parameter transformation is used.

## Request Body Within Data-Source or Task

HTTP requests that are part of a regular data-source definition or a task have the same parameters and parameter values in the input xml for generating a request body that are declared in endpoints.xml and whose values can be different after a parameter transformation than in the original request.