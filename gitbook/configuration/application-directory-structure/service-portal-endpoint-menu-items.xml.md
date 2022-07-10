# service-portal-endpoint-menu-items.xml

The **optional** file `service-portal-endpoint-menu-items.xml` enables the creation of additional, user-defined pages in the service portal.

![service portal endpoint menu items](https://cdn.openendpoints.io/images/gitbook/service-portal-endpoint-menu-items-image.png)

Pages of type `form` and `content` can be optionally organized under a menu-folder. The maximum supported depth of menu items is one.

## Form

The `form` consists of two pages: the "form" to enter data, and the "result" of the form submit. Both pages are defined by a transformer returning an HTML page with mime-type `application/html`.

The "form" (=the page returned by calling the form-endpoint) may contain form elements, but without a `<form>` tag and without a submit button. The form data are submitted to the `result-endpoint`.

## Content

The `content` consists of a single page defined by a transformer returning an HTML page with mime-type `application/html`.
