# Conditional Success Action

Different results may be required for the same endpoint based on certain criteria. It is possible to define any number of success elements such as:

```xml
<endpoint name="foo">
    <success if="${language}" equals="en">
        <response-from-static filename="path-to-english-file"/>
    </success>
    <success if="${language}" equals="de">
        <response-from-static filename="path-to-german-file"/>
    </success>
    <success if="${language}" notequals="es">
        <response-from-static filename="path-to-english-file"/>
    </success>
    <success>
        <!-- no condition => "otherwise" -->
        <response-from-static filename="path-to-spanish-file"/>
    </success>
</endpoint>
```

The conditions are considered in the order they’re written in the file, so put more general “catch-all” items at the bottom and more specific “if...” items at the top.

Parameters may also be used in the "equals" or "notequals" attribute. You could for example create a condition like

```xml
<success if="${delivery-address}" notequals="${invoice-address}">
```

Only `if=".." equals=".."` and `if=".." notequals=".."` are available.

If the parameter has a value like `foo||bar` i.e. created as a result of a request such as `?param=foo&param=bar` then the `equals=".."` will check if _any_ of the values match, and `notequals=".."` will check that _none_ of the values match the value.
