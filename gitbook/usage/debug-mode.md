# Debug Mode

By default, OpenEndpoints does not save any data that was transferred with a request.

Sometimes, however, it can make sense to have data transfer tracking available for error analysis, for example. This is what the **debug mode** and **verbose** is for.

## Working Principle

With the parameter transformation, OpenEndpoints generates a parameter-transformation-input.xml and - after successful transformation - a parameter-transformation-output.xml. These are only stored in memory and not persisted to disk, by default.

When Debug Mode is activated, these two files are saved in the request log and can be downloaded from the Service Portal. (If the transformation fails, the output.xml is not generated).

{% hint style="warning" %}
#### Only works with Parameter Transformation

The debug parameter will be silently ignored in case the endpoint does not use **Parameter Transformation**.
{% endhint %}

## Usage

To use the debug mode, **either** 2 conditions must be met at the same time:

1. The debug mode must be allowed for this application in the service portal.
2. An additional parameter is added to the request: **debug=true**

**or**

1. The **verbose=true** parameter must be sent
2. The request has an error (4xx or 5xx)

The intention of verbose=true is to capture errors, therefore verbose does not require the application have "debug enabled", in contrast to sending debug=true which captures all requests and thus does require the application to have "debug enabled".

![debug mode usage image](https://cdn.openendpoints.io/images/gitbook/debug-mode-usage.png)

## Clear Debug Log

Click "Clear debug log" to delete all files created with debug/verbose mode.

![debug mode clear image](https://cdn.openendpoints.io/images/gitbook/debug-mode-clear-debug-log.png)
