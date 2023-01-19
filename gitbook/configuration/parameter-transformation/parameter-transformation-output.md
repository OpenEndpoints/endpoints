# Parameter Transformation Output

The **parameter-transformation-xslt** will use **parameter-transformation-input.xml** as an input and will generate **parameter-transformation-output.xml**.

The required output scheme is:

```xml
<parameter-transformation-output>
    <parameter name="foo" value="xxx"/>
    <parameter name="fang" value="xxx"/>
    ...
    <parameter name="long" value="xxx"/>
<parameter-transformation-output>
```

* Each parameter existing in endpoints.xml must be present in the output, except if the parameter has a default-value (which will be applied if the parameter were missing in the output).
* Output of a parameter not existing in endpoints.xml will raise an error.
* If the same parameter appears multiple times, then later values override earlier values.

## Raise Custom Error

Optionally an \<error> tag can be added. If existing, an error will be raised. The custom error message is taken from this tag. Note that an empty error tag \<error/> will also raise an error, but with an empty error message.

```xml
<parameter-transformation-output>
    <error>This is my error-message</error>
    <parameter name="foo" value="xxx"/>
    <parameter name="fang" value="xxx"/>
    ...
    <parameter name="long" value="xxx"/>
<parameter-transformation-output>
```
