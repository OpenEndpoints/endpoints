# Tasks

A task is like a ”secondary action” assigned to an endpoint. The primary action is the \<success> and \<error> action described in [Types of Endpoints](../types-of-endpoints/). The "secondary" action may contain zero or many tasks, that will be executed once the primary action has been completed successfully.

{% hint style="info" %}
#### Task vs Primary Action

The task does some action, but the response body of the task is not part of the response to the client's request. The response to the request might be a simple "status 200", while the request had triggered the execution of a task which did have a response body.
{% endhint %}

With a task you can

* send a request to any API => [HttpRequest Task](httprequest-task.md)
* send emails with attachments => [Email Task](email-task.md)

Each \<task> is a set of commands embedded into an endpoint-definition in the endpoints.xml:

```xml
<endpoint name="foo">
    <success>...</success>
    <error>...</error>
    <!-- zero or many tasks -->
    <task class="...">
       ...
    </task>
    <task class="...">
       ...
    </task>
</endpoint>
```

### Parallel or Subsequent Execution of Tasks

Offer-Ready uses multiple cores, if available. If not specified otherwise, tasks are executed in parallel and will be finished in an arbitrary order. In order to determine a distinctive order of tasks, read [Parallel or Subsequent Execution of Tasks](parallel-or-subsequent-execution-of-tasks.md).

### Intermediate Values

If a task requires the output from a second task as an output, you may use [Intermediate Values](intermediate-values.md).
