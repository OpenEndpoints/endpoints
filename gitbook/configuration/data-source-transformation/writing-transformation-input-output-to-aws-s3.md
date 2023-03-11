# Writing Transformation Input/Output to AWS S3

There is the possibility of adding instructions to output the input/output of the transformation to AWS S3.

    <transformer data-source="spheres-fo">
      <write-input-to-aws-s3>
        <folder>foo/bar</folder> <!-- optional -->
        <tag name="foo">bar</tag> 
        <tag name="abc">def</tag> 
      </write-input-to-aws-s3>
      ...
      <write-output-to-aws-s3> 
        ...
      </write-output-to-aws-s3>
    </transformer>

This creates objects in the S3 bucket which have the tags as specified, the correct Content-Type, and in addition a tag called "environment" which is either "preview" or "live".
