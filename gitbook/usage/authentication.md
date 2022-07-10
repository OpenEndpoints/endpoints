# Authentication

Each request to OpenEndpoints requires a mandatory hash parameter:

{% hint style="info" %}
#### Example

[https://yoursever.com/{application}/{endpoint}?hash=7eba31be11799ccedf6fdd1d932ee1d2022ee22b1fea35baead5c92179372e9e](https://yoursever.com/%7Bapplication%7D/%7Bendpoint%7D?hash=7eba31be11799ccedf6fdd1d932ee1d2022ee22b1fea35baead5c92179372e9e)
{% endhint %}

The hash is a SHA-256 hex calculated from several input values. On each request the server calculates the expected value of the user-supplied hash parameter. If the supplied value does not match the expected value the request will be denied.

The hash may be supplied as uppercase or lowercase in the request.

## Secret Key(s)

In the `security.xml` file under application you have to create one or many secret keys.

```xml
<security>
    <!--it is mandatory to have at least 1 secret key-->
    <secret-key>any-string</secret-key>
    <!--optionally you can define any mumber of additional secret keys-->
    <secret-key>optional-another-string</secret-key>
</security>
```

Any secret key in `security.xml` will be a valid input to determine the expected calculated hash. The advantage of having more than one secret key is that you can implement a rotation of secret-keys without interruption of services:

1. Add a new secret key
2. Adopt new hash values in your applications or web forms (using the new secret key)
3. Delete the old secret key

## Calculation of the Hash

Input of the SHA256 function is the concatenated string of the following inputs:

1. Name of the endpoint
2. The values of all the parameters listed in the `<include-in-hash>` block of the endpoint, in the order in which they are listed there. (If there is no `<include-in-hash>` section, or there is but it's empty, then no parameters are added to the hash's source string for this step.)
3. Environment name (either “live” or “preview”)
4. Any secret key from the security.xml file

{% hint style="danger" %}
#### Potential Source of Error

Note that if you use **parameter-transformation** the parameter value taken for the calculation of \<include-in-hash> commands is the value **after** transformation, not the originally submitted value.
{% endhint %}

## Example Calculation

```xml
<security>
    <secret-key>openendpoints</secret-key>
</security>
```

```xml
<endpoint name="helloworld">
    ...
    <include-in-hash>
       <parameter name="foo"/>
        <parameter name="long"/>
    </include-in-hash>
      ...
</endpoint>
```

Assume that

```
# parameter name="foo" value="abc"

```

{% hint style="info" %}
#### Expected Value LIVE environment

Input String = "helloworld" & "abc" & "def" & "live" & "openendpoints"

SHA256("helloworldabcdefliveopenendpoints")

\= 82bb6e7f675a8d872688cb593a64f615b37f88478d7fed8705496d3e7a1c2699
{% endhint %}

{% hint style="info" %}
#### Expected Value PREVIEW environment

SHA256("helloworldabcdef**preview**openendpoints")

\= 4afcbe21891e5be6762f495958659a25950a83e7c52f13594cbebe43cfdd9bf4
{% endhint %}

## Include-In-Hash Use Cases

The possibility to include parameter values in the hash calculation can be used to implement use cases like:

* Send a link to web form having some distinct mandatory input which shall not be changed. Changing the value of that parameter would result in a different expected hash value. Hence, the form will only work with the (unchanged) value.
* Having the same endpoint implemented for different web forms or different applications could use a hidden parameter indicating the originating source. The expected hash can be different for each originator, making it impossible to modify that value.
* Generate the hash in combination with a timestamp. You can invalidate the request after a certain time - and the timestamp can not be modified because this would change the expected hash value.
