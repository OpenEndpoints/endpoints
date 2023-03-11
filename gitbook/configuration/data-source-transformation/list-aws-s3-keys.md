# List AWS S3 Object Keys

This lists the most recent object keys (filenames) out of the bucket specified in the aws-s3-configuration.xml file (see example-customer for example format.).

“Most recent” means the keys of the objects with the most recent last modified timestamp.

The command looks like:

    <aws-s3-keys limit="100">
        <folder>foo/bar</folder> <!-- optional -->
        <match-tag name="foo">bar</match-tag>
        <match-tag name="abc">def</match-tag>
    </aws-s3-keys>

and the results look like:

    <aws-s3-keys>
        <object key="folder/xyz.xml"/>
        ...
    </aws-s3-keys>
