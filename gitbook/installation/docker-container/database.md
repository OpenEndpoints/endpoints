# Database

A PostgreSQL database is required prior to installing OpenEndpoints.

Database schema creation is not necessary; the software does that on startup.

The following data is saved in it:

* **Request log:** This saves metadata for each call, but not the data explicitly transmitted when the call is made. An exception is the debug mode, in which, as an exception, the data transferred when the call is made can also be saved.
* **Incremental IDs:** If the feature is used to provide incremental IDs, the last ID used is saved in the database.
* **Forward-to-Endpoint-URL:** OpenEndpoints can generate short URLs, which, when called, cause a request to a previously-specified endpoint including previously-saved request parameters.
* **Service Portal:** Application Data and Logins.
