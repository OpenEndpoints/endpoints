# Publish Application

{% hint style="info" %}
#### The process to publish an application requires 2 steps:

1. Commit changes in your application directory to Git
2. Use the "Publisher" to pull the latest revision of your application into the software.
{% endhint %}

Open the application you want to publish:

![open appliction you want to publish image](https://cdn.openendpoints.io/images/gitbook/publish-application-open-app-to-publish.png)

Navigate to "**Publish**" in the main navigation. The Publisher lets you publish the latest version of your Git to Preview or Live Environment - see: [Environments](../../usage/environments.md).

![publish application](https://cdn.openendpoints.io/images/gitbook/publish-applictaion-publish.png)

## Publish to Preview

This option will:

1. Load the latest available revision from your Git repository
2. Check the new configuration for consistency and errors. If an error is found, the new version will not overwrite any existing status. A detailed description of the error is displayed.
3. On success the new revision will replace the previous configuration.

Promote Preview to Live

This option will not pull a new version from the Git, but simply copy the existing configuration from Preview to Live. No further check of consistency is required, as this had been done already when publishing that version to Preview.
