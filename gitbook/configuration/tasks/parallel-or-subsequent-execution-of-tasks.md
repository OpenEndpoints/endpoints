# Parallel or Subsequent Execution of Tasks

Offer-Ready uses multiple cores, if available. If not specified otherwise, tasks are executed in parallel and will be finished in an arbitrary order.

> #### Paralell execution of primary action and tasks
>
> Note that - as a default - the primary task (data transformation) and all tasks are executed in parallel.

Therefore if one HTTP request depends on another previous one having completed first, that will not work without declaring this dependency.

You may optionally assign an id attribute to the task element:

```xml
<task class="endpoints.task.HttpRequestTask" id="foo">
  ...
</task>
```

You may insert an element \<after task-id="..."/> into any task that needs to be executed after foo…

```xml
<task class="endpoints.task.HttpRequestTask">
    <after task-id="foo"/>
    ...
</task>
```

Note that on using [Intermediate Values](intermediate-values.md) the software will automatically determine the order of execution such that intermediate outputs are created before they are required as inputs. Intermediate values and “after” elements can be used in parallel.
