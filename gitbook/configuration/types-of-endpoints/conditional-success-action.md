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

The if-condition technically is a variable that will expand a parameter value.

Variables may also be used in the "equals" or "notequals" attribute. You could for example create a condition like

```xml
<success if="${delivery-address}" notequals="${invoice-address}">
```

Only `if=".." equals=".."` and `if=".." notequals=".."` are available.
