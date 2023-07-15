# Additional Useful Data Source Types

## Literal XML

The data-source command `<literal-xml>` lets you define xml output directly and "literally" in the data-source definition file.

```xml
<!-- Data source definition -->
<data-source>
    <literal-xml>
        <any-tag/>
    </literal-xml>
</data-source>
```

The root tag `<literal-xml>` is not included in the data-source xml output. In the example above, the generated xml will be:

```xml
<!-- Generated XML -->
<transformation-input>
    <any-tag/>
</transformation-input>
```

This data-source-type can be perfectly used in combination with parameter placeholders. For example, you can use something like this:

```xml
<!-- Data source definition -->
<data-source>
    <literal-xml>
        <any-tag>${foo}</any-tag>
    </literal-xml>
</data-source>
```

If `${foo}` equals "hello world", the data-source output will be:

```xml
<!-- Generated XML -->
<transformation-input>
    <any-tag>hello world</any-tag>
</transformation-input>
```

Note that the contents of <literal-xml> must be elements, simply placing text straight under the <literal.xml> element will not work.

## Application Introspection

This content-source produces as its output a description of the entire application directory structure (=your configuration).

The generated content has a root-tag `<application-introspection>` and returns

* `<directory name="x">` for any directory
* `<file name="x"/>` for all XML files. The content of the XML file is included as a child of this tag, except the directory `xml-from-application`. (Use the `<xml-from-application>` data source, not `<application-introspection>` to load content from such files.)
* `<file name="x"/>` for all non-XML files. In this case the content is not in any way included.

{% hint style="warning" %}
#### XML files must actually contain XML

If a file named `*.xml` does not in fact contain well-formed XML, this is an error.
{% endhint %}

{% hint style="info" %}
#### No expansion of endpoint parameters

Parameters like `${foo}` found in the file are not expanded in this type of content-source.
{% endhint %}

## On-Demand Incrementing Number

\<OpenEndpoints/> can generate unique auto-increment values and provide them as a data-source. Read [On-Demand Incrementing Number](on-demand-incrementing-number.md) for more details.
