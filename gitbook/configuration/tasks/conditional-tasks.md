# Conditional Tasks

Any task can be made conditional, that means the task will only be executed if some parameter value matches a condition.

```xml
<task class="..." if="${foo}" equals="true">
    ...
<task>
```

The condition can have operator `equals` or `notequals`.

Note the synatx of the `if` condition: We use a parameter placeholder. The value of the parameter expands into the `if` attribute.
