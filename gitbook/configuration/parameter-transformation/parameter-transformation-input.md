# Parameter Transformation Input

Inputs from request and data from optionally added data sources are automatically placed into a temporary XML called \<parameter-transformation-input>.

The custom XSLT ("parameter-transformation-xslt") will be applied to this XML.

OpenEndpoints will automatically insert additional useful tags:

## Tags added to \<input-from-request>

* **all parameters submitted** in the originating request, having \<parameter name="xxx" value="xxx"/>. This explicitly includes the system parameters **hash** and **environment** (if not omitted; otherwise the parameter is not existing).
* **endpoint:** name of the endpoint that has been called.
* **debug-requested:** if existing the request had a parameter "debug=true". Otherwise this tag will be omitted.
* **\<http-header name-lowercase="user-agent">Foo\</http-header>** This may be present multiple times, or not at all. HTTP Headers are case insensitive so e.g. “User-Agent” and “user-AGENT” are the same header. Therefore these are normalized to all lowercase.
* **\<cookie name="Session">12345\</cookie>** This may be present multiple times, or not at all. Cookies on the other hand are case sensitive, so it’s possible to have “Session” and “SESSION” as two different cookies with different values, so these aren’t normalized to lowercase.
* **ip-address**

## Tags added to \<input-from-application>

* **application:** the name of the application
* **application-display-name:** the display-name of the application, if available from the database.
* **git-revision:** if the application was published from Git via the Service Portal then this contains the Git hash for the revision. (If the application is deployed in "single application mode" then this tag is omitted.)
* **debug-allowed:** if existing debugging is set to "allowed" in the database. Otherwise this tag will be omitted.
* **secret-key:** one separate tag for each secret-key
* **random-id-per-endpoint:** the database request-log adds a random id per request per application
* **base-url:** The base-url of the application is taken from an environment variable.

### Example: Request sent from web form

```xml
<parameter-transformation-input>

    <!-- This file shows the example input to a parameter-transformation. In this case input was submitted from a webform (POST or GET) -->

        <input-from-request>
            <endpoint>my-endpoint</endpoint>
            <debug-requested/>

            <!-- only present when debug=true; otherwise missing -->
            <environment>preview</environment>
            <hash>ed99c4d96dac277331f08216bf7227e10dab64edcd05d7e02ad4d090c22f369b</hash>
            <http-header name-lowercase="user-agent">Foo</http-header>
            <cookie name="Session">12345</cookie>

            <!-- only present when IP address was available; otherwise missing -->
            <ip-address>1.2.3.4</ip-address>

            <!-- list of all parameters submitted or - if not submitted - having default-value from endpoints xml -->
            <parameter name="some" value="some value"/>
            <parameter name="more" value="some other value"/>
            <parameter name="examples" value="and so on"/>
        </input-from-request>

        <input-from-application>
            <application>application-name</application>
            <application-display-name>The Application</application-display-name>

            <!-- or missing, if not available -->
            <debug-allowed/>

            <!-- only present when debug-mode enabled in service portal; otherwise missing -->
            <!-- if you have multiple secret keys, then all of them will be listed -->
            <secret-key>foo</secret-key>

            <base-url>https://endpoints.com/</base-url>
        </input-from-application>

    <!-- You can add any additional data-sources with the parameter-trasformation element. Data from those will be available below. -->
    <whatever-the-data-source-command-returns/>
</parameter-transformation-input>
```

### Example: Json Payload

The json paylod is converted to xml.

```xml
<parameter-transformation-input>

    <!-- This file shows the example input to a parameter-transformation. In this case input was submitted within a JSON body (POST) -->
    <input-from-request>
        <endpoint>my-endpoint</endpoint>
        <debug-requested/>

        <!-- only present when debug=true; otherwise missing -->
        <environment>preview</environment>
        <hash>ed99c4d96dac277331f08216bf7227e10dab64edcd05d7e02ad4d090c22f369b</hash>

        <!-- only present when IP address was available; otherwise missing -->
        <ip-address>1.2.3.4</ip-address>

        <!-- the json body is converted to xml -->
        <json>
            <some>some value</some>
            <more>some other value</more>
            <examples>and so on</examples>
        </json>
    </input-from-request>

    <input-from-application>
        ...
    </input-from-application>

    <!-- You can add any additional data-sources within the parameter-trasformation tag. Data from those will be available below. -->
    <whatever-the-data-source-command-returns/>
</parameter-transformation-input>
```

### Example: XML Paylod

```xml
<parameter-transformation-input>
    <!-- This file shows the example input to a parameter-transformation. In this case input was submitted within a XML body (POST) -->
    <input-from-request>
        <endpoint>my-endpoint</endpoint>
        <debug-requested/>
        <!-- only present when debug=true; otherwise missing -->
        <environment>preview</environment>
        <hash>ed99c4d96dac277331f08216bf7227e10dab64edcd05d7e02ad4d090c22f369b</hash>
        <!-- only present when IP address was available; otherwise missing -->
        <ip-address>1.2.3.4</ip-address>
        <!-- the xml body as submitted -->
        <xml>
            <some>some value</some>
            <more>some other value</more>
            <examples>and so on</examples>
        </xml>
    </input-from-request>

    <input-from-application>
        ...
    </input-from-application>

    <!-- You can add any additional data-sources with the parameter-trasformation element. Data from those will be available below. -->
    <whatever-the-data-source-command-returns/>
</parameter-transformation-input>
```

## How-to debug

1. Create the parameter-transformation-tag
2. Allow debugging and send a request with debug=true. For details see [Debug Mode](../../usage/debug-mode.md)
3. The generated parameter-transformation-input.xml is available in the request-log.
