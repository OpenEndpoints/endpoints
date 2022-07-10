# Request Log Task

You can add custom key/value pairs to your request-log.

For example, if you want to add the "country" submitted in your contact workflow to your request log, you simply create a parameter ${country} and add this task to your endpoint:

```xml
<task class="endpoints.task.RequestLogExpressionCaptureTask" key="Country" value="${county}" />
```

If an endpoint is forwarding the request to another endpoint, then the initial "parent" endpoint will remain the only entry in the log. However, you can add key/value pairs to this "parent" log entry in each subsequent step.
