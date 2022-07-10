# Add New Application

The term ”application” is used to describe a piece of configuration. OpenEndpoints can serve multiple applications simultaneously. You can create different projects with different application directories and run them in parallel.

An application is configured with a directory of files - mainly XML files. The structure of the directory is explained in detail here: [Application Directory Structure](../../configuration/application-directory-structure/).

OpenEndpoints pulls the application directory from a Git. When publishing, the last version is pulled from the Git and your configuration is updated.

## Add or Edit Application

Click `Add New Application` to create your first application.

![add new application](https://cdn.openendpoints.io/images/gitbook/add-new-application-add.png)

This will open the screen to add new or edit existing applications:

![add new appplication : settings](https://cdn.openendpoints.io/images/gitbook/add-new-application-settings.png)

* Choose an **application name**, which will become part of the endpoints URL
* The Display Name is used in the user interface and will also be available as an **input-from-application** in [Parameter Transformation](../../configuration/parameter-transformation/).
* The Git URL shall point to the root folder of your [Application Directory Structure](../../configuration/application-directory-structure/).

After successfully adding a new application you will find your new application in the "Change Application" Screen:

![add new application : change](https://cdn.openendpoints.io/images/gitbook/add-new-application-change.png)
