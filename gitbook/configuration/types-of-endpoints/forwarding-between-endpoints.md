# Forwarding Between Endpoints

This type of syntax specifies that an endpoint "step-1" is executed and on success another endpoint "step-2" will be called.

The result of the original request (i.e. all parameters) is used as the request to the endpoints being forwarded to:

```xml
<endpoint name="step-1">
    <success>
        <forward-to-endpoint endpoint-name="step-2"/>
    </success>
</endpoint>
```

All parameter values are forwarded to the new endpoint.

System parameters such as user agent, client IP address and file uploads are all available at the endpoint forwarded to. They are inherited to the forwarded endpoint.

It’s possible to chain the execution of any number of endpoints in this manner (e.g. endpoint e1 forwards to e2 which itself forwards to e3). A circular chain of such references is not allowed as the processing of such a chain would never end.

The “redirect” from one endpoint to another happens within the Endpoints software; no redirect is actually sent to the user’s browser.

## Request Log Behaviour

Only one “request log” gets written, despite a chain of multiple endpoints being processed. Only the first “parameter transformation input/output” is saved with that “request log” entry, despite each endpoint in the chain potentially having its own parameter transformation.
