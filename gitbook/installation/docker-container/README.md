# Docker Container

OpenEndpoints is software written in Java. The code is open source and licensed under Apache License with Commons Clause - see [https://openendpoints.io/license](https://openendpoints.io/license).

For the deployment we recommend using [Docker](https://www.docker.com/). A public Docker container is available at:

```docker
public.ecr.aws/x1t6d0t7/endpoints-he
```

## Using Saxon-PE

By default the public Docker container will install the free XSLT processor Saxon-HE. This is sufficient for most purposes.

In order to deploy OpenEndpoints with the commercial version Saxon-PE you are required to buy a license for Saxon-PE (Professional Edition) and create a Docker container using that edition. The license isn't expensive and it's absolutely worth the money.

Here are the steps you need to take:

1. Purchase a Saxon-PE license at [https://www.saxonica.com/shop/shop.html](https://www.saxonica.com/shop/shop.html). You will get two files: the JAR file containing the Saxon-PE code, and also a Saxon-PE license file.
2. Install Java 17 and Maven on your computer if you have not already done so.
3. Check out the OpenEndpoints Git repo to your computer if you have not already done so.
4. Execute the following command to install the Saxon-PE JAR that you have purchased into your local Maven repository on your computer: `mvn install:install-file -Dfile=<path to your Saxon-PE file> -DgroupId=net.sf.saxon -DartifactId=Saxon-PE -Dversion=9.7.0.18 -Dpackaging=jar` replacing your path to your downloaded file as appropriate. Keep the -Dversion the same, no matter what version you've actually downloaded.
5. Copy the Saxon-PE license file to your Git checkout, placing it in the path `saxon-pe/saxon-license.lic`.
6. Execute the following command to build OpenEndpoints with Saxon-PE: `mvn -DSaxon=PE clean package`
7. Build the Docker image using a command like `docker build -t endpoints-pe .`
8. Push the Docker image to your Docker repository. Note that the terms of the Saxon-PE license do not allow you to make this Docker image public in any way.
