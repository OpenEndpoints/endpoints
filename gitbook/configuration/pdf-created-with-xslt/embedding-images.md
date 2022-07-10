# Embedding Images

Offer-Ready is built around the concept of dynamically generated content. But of course parts of the content required to build your endpoints may not require being dynamically generated, because they already exist as some sort of “static” content.

Static content such as images or files may be stored in the application’s static directory.

## Image Paths in PDF

Image paths within an XSL-FO file may have an absolute path (to some URI), or a relative path.

* Absolute paths will be "executed" by the PDF reader. Make sure that the path is accessible from your client's device.
* Relative paths will use the application’s static directory as a root directory. Images will be embedded in the generated PDF.
