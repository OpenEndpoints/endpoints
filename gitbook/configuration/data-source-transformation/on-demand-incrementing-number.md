# On-Demand Incrementing Number

Sometimes it may be required to insert a unique incremental id into a generated content.

Depending on the specific business use case, the incremental id may be unique “perpetual”, or it may be required to re-start the counter every month or every year.

| **Type**  | **Example Value** |
| --------- | ----------------- |
| perpetual | 23456             |
| year      | 2020-0068         |
| month     | 2020-01-0017      |

{% hint style="info" %}
#### Request UUID

In addition, each request submitted to \<OnestopEndpoints/> gets assigned a globally unique [UUID](https://en.wikipedia.org/wiki/Universally\_unique\_identifier) in the transaction-log. You can access this id in the parameter-transformation, but it is not available as a data source.
{% endhint %}

The command to fetch a new auto-increment value is a **data-source**.

```xml
<data-source>
    <on-demand-incrementing-number type="month"/>
</data-source>
```

Whenever a transformer has a data-source with that command the auto-increment will be triggered. The type attribute may take the values “perpetual”, “year” or “month”. The numbers are unique within the application.

{% hint style="info" %}
#### Formatting

Note that the data source returns a number only. If the request has the incremental number "17" for the current month, the value provided (for type="month") will be 17. In order to get something like 2020-01-00**17** you need to build such format with XSLT.
{% endhint %}

## Creation on-demand

The term “on-demand” refers to the fact the number does not get consumed unless it is requested. If one value (e.g. type=”month” ) is consumed, other values (e.g. type=”year”) are not automatically consumed as well.

The value is only consumed if the endpoints request is successful; if the request is not successful the number is again made available to future requests. The numbers do not have any “holes” or missed-out numbers, so are suitable for use in invoice numbers.

## Unique per Request & Data-Source

The same endpoint may contain several different transformers, some of which might call the same data-source. For example, the endpoint may include a task to send an email, and the email-body and some attachment both require the same data-source. In this case - the data source is used twice within the same request - they both see the same number. The new incremental id is created per request, not per use of the (same) data-source. However, if you use 2 different data-sources, both calling an auto-increment, then 2 different ids will be created.

## Database

The incremented values will be stored in the database. Changing the values in the database will effect the next generated number.
