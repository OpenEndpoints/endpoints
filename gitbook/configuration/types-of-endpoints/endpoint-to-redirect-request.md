# Endpoint to Redirect Request

This type of syntax specifies that a redirect of the request should be performed. The body of the `<redirect-to>` specifies where. Variables, if present, are replaced, in the body.

For example you can redirect a successful request to a "thank.you.html" url.

```xml
<endpoint name="foo">
    <success>
        <redirect-to>https://www.mysite.com/thank.you.html</redirect-to>
    </success>
</endpoint>
```

Parameters like `${foo}` in the body are replaced.

If you use variables, we recommend to use this optional element to prevent a malicious request redirecting somewhere wrong:

```xml
<endpoint name="foo">
    <success>
        <redirect-to>${my-destination}</redirect-to>
        <!-- optional zero or many prefeix whitlist entrie -->
        <redirect-prefix-whitelist-entry>http://www.mywebsite.com</redirect-prefix-whitelist-entry>
        <redirect-prefix-whitelist-entry>http://docs.mywebsite.com</redirect-prefix-whitelist-entry>
    </success>
</endpoint>
```

If no such tag is present, redirect to any URL is allowed. If one or more are present, the URL being redirected to must start with the prefix of one of them; otherwise this is an error.
