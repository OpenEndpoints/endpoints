# Web Form Controls Having Multiple Values

A distinction must be made between two technically different topics:

* The web form containing multiple controls having the same `name` attribute.
* The web form containing a single control, but which allows multiple values to be selected.

## Multiple controls having the same name attribute

According to w3c standard HTML forms can have multiple controls sharing the same name attribute. See: [https://www.w3.org/TR/html52/sec-forms.html](https://www.w3.org/TR/html52/sec-forms.html)

For example:

```html
<form>
  <input type="checkbox" name="topping" value="bacon" />
  <input type="checkbox" name="topping" value="cheese" />
  <input type="checkbox" name="topping" value="onion" />
</form>
```

OpenEndpoints will combine all values submitted into a single input-from-request parameter, separated by a **multiple-value-separator**. The default separator is a double pipe `||`.

The above example would result in a parameter "topping" that has the following value:

```xml
<parameter name="topping" value="bacon||cheese||onion"/>
```

It is possible to define any other multiple-value-separator. See: [Multiple parameters supplied with the same name](../configuration/endpoint-parameter.md#multiple-parameters-supplied-with-the-same-name).

## HTML input types allowing multiple values

Some HTML form controls allow to submit multiple values for a single control, for example:

```html
<select name="topping" multiple="true">
  <option value="bacon">Bacon</option>
  <option value="cheese">Cheese</option>
  <option value="onion">Onion</option>
</select>
```

On selecting multiple values, this would be submitted by the user agent in the same way as having multiple controls with the same name attribute. **The result will be the same as above in OpenEndpoints:**

```xml
<parameter name="topping" value="bacon||cheese||onion"/>
```

## Uploading multiple files

It is possible to upload several files at the same time:

```html
<input type="file" name="foo" multiple="true" />
```

See: [Web Form With File Upload](web-form-with-file-upload.md)
