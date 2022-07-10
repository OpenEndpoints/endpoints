# HTML Snippet embedded with Java Script

Even on a static website there is always content that changes regularly and can be loaded from a database or a web service, for example. OpenEndpoints is perfect for converting external content into HTML, which can be seamlessly integrated into your own website.

## HTML Sniplets

Build an [Endpoint to Return XSLT Transformation](../configuration/types-of-endpoints/endpoint-to-return-xslt-transformation.md). Do **not** use the `download-filename` attribute in this case.

The generated HTML is inserted directly into the \<div>. Therefore, the HTML (generated with XSLT) should not contain a \<head> or \<body> tag, but actually only the content that is to be inserted!

Set content-type "text/html" in the transformer:

```xml
<transformer data-source="...">
    <xslt-file name="..."/>
    <content-type type="text/html; charset=utf-8"/>
</transformer>
```

## Load Snippet into Web Page

Java Script can be used to load the html returned from the endpoint into a `<div>`.

In this example the script was taken from [https://www.w3schools.com/lib/w3.js](https://www.w3schools.com/lib/w3.js).

```html
<html>
  <head>
    ...
    <script>
      function includeHTML() {
        var z, i, elmnt, file, xhttp;
        /* Loop through a collection of all HTML elements: */
        z = document.getElementsByTagName("*");

        for (i = 0; i < z.length; i++) {
          elmnt = z[i];
          /*search for elements with a certain atrribute:*/
          file = elmnt.getAttribute("w3-include-html");

          if (file) {
            /* Make an HTTP request using the attribute value as the file name: */
            xhttp = new XMLHttpRequest();
            xhttp.onreadystatechange = function () {
              if (this.readyState == 4) {
                if (this.status == 200) {
                  elmnt.innerHTML = this.responseText;
                }
                if (this.status == 404) {
                  elmnt.innerHTML = "Page not found.";
                }

                /* Remove the attribute, and call this function once more: */
                elmnt.removeAttribute("w3-include-html");
                includeHTML();
              }
            };
            xhttp.open("GET", file, true);
            xhttp.send();

            /* Exit the function: */
            return;
          }
        }
      }
    </script>
  </head>
  <body>
    ...
    <div w3-include-html="[path-to-your-endpoint]"></div>
    ...
    <script>
      includeHTML();
    </script>
  </body>
</html>
```
