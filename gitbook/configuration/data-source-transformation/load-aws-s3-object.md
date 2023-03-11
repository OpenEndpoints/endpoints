# Load AWS S3 Object (File)

This reads a particular object (file) from AWS S3. It is assumed that this object contains XML data. The command looks like:

    <aws-s3-object key="folder/foo.xml"/>

and the results look like:

    <aws-s3-object key="folder/foo.xml">
        ... file contents (XML) ...
    </aws-s3-object>
