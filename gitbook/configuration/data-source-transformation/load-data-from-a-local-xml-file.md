# Load Data From a Local XML File

In contrast to content loaded from the internet, XML already existing within the application does not require to be loaded each time. This makes it a good choice for large files. It is possible to use XML files with more than 100,000 lines of content without causing any performance issues. In general, the limiting factor is the performance of the XSLT transformation rather than the size of the data source.

You can place such files under the the `xml-from-application` directory within the application. You can load the content of this file into the data-source:

```xml
<!-- Data source definition -->
<data-source>
    <xml-from-application
       file="path-to-xml-file-in-xml-from-appication-directory"/>
</data-source>
```

You can use placeholders to fill in endpoint parameter values into the file attribute:

```xml
<!-- Data source definition -->
<data-source>
  <xml-from-application file="${some-parameter}.xml"/>
</data-source>
```

If the file can not be found, an error will be raised. To avoid that, add an attribute `ignore-if-not-found="true"`. The data-source in this case will look like if the content was not requested at all.

```xml
<!-- Data source definition -->
<data-source>
    <xml-from-application file="${some-parameter}.xml"
                          ignore-if-not-found="true"/>
</data-source>
```
