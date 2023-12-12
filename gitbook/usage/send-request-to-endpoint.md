# Send Request To Endpoint

An application comprises of multiple endpoints. Each endpoint has a name which is used in the URL. Which endpoint is requested is specified when the user calls the application.

{% hint style="info" %}
#### Endpoint URL

**https://{base-url}/{application}/{endpoint}**
{% endhint %}

## Send Request to Endpoint

OpenEndpoints knows 2 types of data sources that can serve as input for the content transformation:

* data that is transferred with the request, and
* data that is loaded from other sources when the endpoint is executed in the background.

The following methods are available to transfer data with the request:

* GET Request with parameters URL-encoded
* POST Request containing parameters. This is the default when an HTML \<form> is used. The default content type is `application/x-www-form-urlencoded`. Use `multipart/form-data` to add any number of file uploads.
* POST Request with application type `application/xml`: In this case arbitrary XML is supplied (in the request body), which is passed to the parameter-transformation-input structure, inside the `<input-from-request>` element instead of the normal `<parameter>` elements.
* POST Request with application type `application/json`: In this case arbitrary JSON is supplied (in the request body), which is converted to XML and passed to the parameter-transformation-input structure, inside the `<input-from-request>` element instead of the normal `<parameter>` elements. Note:
  * Any characters which would be illegal in XML (for example element name starting with a digit) replaced by `_xxxx_` containing their hex unicode character code.
  * Note that if any JSON objects have a key `_content`, then a single XML element is created, with the value of that `_content` key as the text body, and other keys from the JSON object being attributes on the resulting XML element.

## Special Parameters

There are the following special request parameters:

| Parameters    |           | Possible Values                                                     |
| ------------- | --------- | ------------------------------------------------------------------- |
| `hash`        | mandatory | calculated sha-256 hash - see [Authentication](authentication.md)   |
| `environment` | optional  | `preview` or `live` (default) - see [Environments](environments.md) |
| `debug`       | optional  | `true` or `false` (default) - see [Debug Mode](debug-mode.md)       |

{% hint style="info" %}
#### How to supply special parameters

Special parameters are supplied along with the normal parameters, apart from in the case of a POST request `application/xml` or `application/json` in which case these special parameters are passed as GET parameters
{% endhint %}
