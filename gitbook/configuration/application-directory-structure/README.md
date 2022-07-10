# Application Directory Structure

The entire configuration of an application that is built with OpenEndpoints consists of files in a directory - we refer to this as the **Application Directory**.

An example directory structure is available on Github (public repository):

{% hint style="success" %}
#### Directory Structure With XSD + Example Files

[https://github.com/OpenEndpoints/xsd](https://github.com/OpenEndpoints/xsd)
{% endhint %}

For each configuration file or directory, additional explanations are available in the subsections of this page.

Note that [https://github.com/OpenEndpoints/xsd](https://github.com/OpenEndpoints/xsd) is not a working example application. The aim of the example directory is to present the expected syntax and directory structure.

## Application loaded from Git

The application directory resides in **your own Git repository**. There are 2 [Deployment Modes](../../installation/docker-container/deployment-modes.md):

* **Multi Application Mode (=default):** The Service Portal provides a simple user interface to "publish" the latest version from Git into the software. An integrity check with meaningful error messages is carried out automatically. Most configuration errors are detected in this way.
* **Single Application Mode:** You create a new Docker image derived from the standard Docker image, which includes the application directory. You need to build a new Docker image each time your configuration has changed.

The multi-application mode also offers the possibility of simple staging - see [Environments](../../usage/environments.md).

## Expected XML sometimes less strict than XSD

The software is a little less strict than what is written in the XSD: Don’t be surprised if you find working examples where the order of elements is slightly different from what is described in the XSD.

## Additional files silently ignored

Any additional files which are present in the application directory, but not required by the software, are silently ignored. For example, if you create an additional directory "**project files**" that will be ignored by the software without raising an error.

## Minimum configuration requirement

Many configuration files are only required if the corresponding features are used.

In any case, these few files must be present for a working configuration:

* **endpoints.xml** having at least 1 endpoint
* **security.xml** having at least 1 secret key
* at least one **transformer**, which as a matter of facts also requires a **data-source**
