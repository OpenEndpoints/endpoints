# Types of Endpoints

In `endpoints.xml`, `<endpoint>` has two sub-sections, `<success>` and `<error>`. What happens in the case of success or error depends on the tags being present. Details are described in the following subsections.

```xml
<endpoint name="some-name-unique-within-same-application">
    <success>
        <! -- tag specifying the specific success action  -->
    </success>
    <error>
        <! -- tag specifying the specific error action  -->
    </error>
</endpoint>
```

If the tag (`<success>` or `<error>`) is missing, or present and empty, this means the server returns an empty 200 OK in the success case and 400 error in the case of failure. This can be useful if the request should simply perform some tasks e.g. send emails.
