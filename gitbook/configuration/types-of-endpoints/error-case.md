# Error Case

In general `<error>` supports the same tags as `<success>`. But there are limitations.

## No forward-to-endpoint

Placing `<forward-to-endpoint>` inside `<error>` is not supported, as this would require “auto increment” numbers to be assigned even in the case of `<error>` (as any arbitrary endpoint might be called, which might require them). It is an explicit design goal to only support auto-increment numbers (e.g. for invoice numbers) in the case of `<success>`.

## Limited Support of Variables

On error the same tags can be used as for `<success>`, but placeholders for client generated parameter values are not supported. An error might have happened during parameter transformation, and therefore client generated parameters are not necessarily available.

{% hint style="info" %}
#### Limited Support of Variables

This limitation not only applies to the use of placeholders in the endpoint-definition, but also for any data-source or XSLT used within a transformation.
{% endhint %}

System generated parameters may be used.

In addition, for the processing of the \<error> tag the following additional parameters are available:

* `${internal-error-text}` - This contains an internal error message. It is important this is not exposed to any end customer, as it might contain security-sensitive information such as “cannot connect to database at IP address 1.2.3.4” etc.
* `${parameter-transformation-error-text}` - In case the request failed because the parameter transformation failed, and a message was set in the `<error>` tag in its output.
