# Directory: fonts

You can embed custom fonts into your generated PDF.

In order for custom fonts to be embedded in a PDF, 2 steps must be set:

1. Upload the TTF files into the `fonts` directory. Only TTF fonts are supported.
2. Declare those fonts in the file `apache-fop-config.xsd`.

## apache-fop-config.xml

Every combination of style and weight of a font must be declared as a font triplet in file `apache-fop-config.xml`:

![directory fonts](https://cdn.openendpoints.io/images/gitbook/directory-font-apache-fop-config.png)

Note that the file-name of the TTF font may be different from the name as used in the font-triplet attribute. The font-name as referred to in the XSLT must be the same as in the font-triplet name attribute (case sensitive).
