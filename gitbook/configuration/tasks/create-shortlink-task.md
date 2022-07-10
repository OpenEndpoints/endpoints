# Create Shortlink Task

Some email programs may have issues with long links. Links to endpoints (containing all parameters) may get long, so this can become a problem.

The “Short Link To Endpoint” feature allows shorter links to endpoints (including all parameters) to be created. This is analogous to [Forwarding Between Endpoints](../types-of-endpoints/forwarding-between-endpoints.md), with the exception that rather than the destination endpoint getting executed immediately, a link is created to the processing of that endpoint.

The task creates a short-link in the database with a random code. The resulting full link, including the code and also including the base URL of the current installation of Endpoints.

The short link looks like this: **\[base-url]/shortlink/RANDOMCODE**.

The generated link is written to an **output intermediate variable**. The concept of intermediate valriables is described here: Intermediate Values.

The shortlink will be auto-deleted in the database after the time specified in `expires-in-minutes`. For example, if you put `expires-in-minutes="1440"` then the link will be available for 1 day. After that time the link will not work any longer.

Use a syntax like the following to create a short link to an endpoint in the variable ${foo}. (You can choose any other variable name, of course).

```xml
<task id="create-link"
      class="endpoints.task.CreateShortLinkToEndpointTask"
      destination-endpoint-name="test"
      output-intermediate-value="foo"
      expires-in-minutes="1440"/>
```

The variable ${foo} can then be used as an input-intermediate-value in a subsequent task.

For example, you can send an email containing ${foo} in the email-body. The xslt (to create the email-body) would look like this:

```xml
<a>
  <xsl:attribute name="href" select="transformation-input/parameters/intermediate-value[@name eq 'foo']/@value"/>
  <xsl:text>Short Link</xsl:text>
</a>
```
