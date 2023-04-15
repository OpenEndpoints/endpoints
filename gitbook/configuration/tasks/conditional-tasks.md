# Conditional Tasks

Any task can be made conditional, that means the task will only be executed if some parameter value matches a condition.

```xml
<task class="..." if="${foo}" equals="true">
    ...
<task>
```

The current set of operators supported are:

* `if="..." equals="..."`
* `if="..." notequals="..."`
* `if="..." isempty="true"`
* `if="..." hasmultiple="true"`
* `if="..." gt="..."`
* `if="..." ge="..."`
* `if="..." lt="..."`
* `if="..." le="..."`

Note the syntax of the `if` condition: Either side can use parameter placeholder.

If the parameter has a value like `foo||bar` i.e. created as a result of a request such as `?param=foo&param=bar`, then the equals=".." will check if any of the values match, and notequals=".." will check that none of the values match the value.

For the `gt`, `ge`, `lt`, `le` operators the comparison values will be treated as numbers (decimal). If either side are empty or not parseable as a number, the comparison is false.

The right hand side of `isempty` and `hasmultiple` can be `true` or `false`.
