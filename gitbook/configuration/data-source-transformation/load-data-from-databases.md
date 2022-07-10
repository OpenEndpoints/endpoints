# Load Data From Databases

## Basic Syntax

The data-source command `<xml-from-database>` fetches rows from a database and transforms rows and columns into XML.

Currently only **MySQL** and **PostgreSQL** are supported. Other databases would require other client JARs that are not provided in the current version of the software.

```xml
<data-source>
    <xml-from-database>
        <jdbc-connection-string><![CDATA[jdbc:postgresql://myserver.com/mydb?user=xxx&password=xxx</jdbc-connection-string]]></jdbc-connection-string>
        <sql>SELECT * FROM mytable WHERE id=?</sql>
        <param>${foo}</param>
    </xml-from-database>
</data-source>
```

Alternative option using the environment-variable to connect to your local endpoints database - in this example (sql!) fetching request details from the request-log:

```xml
<data-source>
    <xml-from-database>
        <jdbc-connection-string from-environment-variable="MY_ENV_VAR"/>
        <sql>SELECT * FROM request_log WHERE request_id=?::uuid</sql>
        <param>${SEARCH_REQUEST_ID}</param>
    </xml-from-database>
</data-source>
```

**\<jdbc-connection-string>** specifies how to connect to the database to perform the query. This element is mandatory.

* If it is present with no attributes, the body of the tab specifies the JDBC URL. Using a CDATA section is recommended to avoid having to perform XML escaping. Don't forget that username and password values must be URL-escaped.
* If it is has an attribute `from-environment-variable="foo"` then the environment variable with that name is read and should contain the JDBC URL. Note that endpoints parameters are NOT expanded in the name of the variable name, to prevent an attacker having access to other environment variables.

**\<sql>** should be self-explanatory :-)

* Endpoint parameters are **NOT** expanded as that would allow SQL injection attacks.
* For PostgreSQL, for non-string parameters, **?::int** or **?::uuid** it is necessary to cast the string supplied by the endpoint parameter into the right type for PostgreSQL.

Zero or more **\<param>** elements, whose body are the contents of any "?" in the \<sql> element. Here, endpoint parameters **ARE** expanded.

Generated output looks like this:

```xml
<!-- Generated output -->
<transformation-input>
    <xml-from-database>
        <row>
            <name-of-column-1>xxx</name-of-column-1>
            <name-of-column-2>xxx</name-of-column-2>
            <name-of-column-3>xxx</name-of-column-3>
        </row>
        <row>
            <name-of-column-1>xxx</name-of-column-1>
            <name-of-column-2>xxx</name-of-column-2>
            <name-of-column-3>xxx</name-of-column-3>
        </row>
    </xml-from-database>
</transformation-input>
```

By default the root-tag of the generated output is \<xml-from-database>. Use the optional **tag attribute** to generate any different root-tag:

```xml
<!-- Data source definition -->
<data-source>
    <xml-from-database tag="my-new-root-tag">
    ...
    </xml-from-database>
</data-source>
```
