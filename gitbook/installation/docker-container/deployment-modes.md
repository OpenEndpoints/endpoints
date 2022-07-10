# Deployment Modes

The application can be deployed in two different ways.

In both scenarios

* Docker is used to deploy the application.
* A PostgreSQL database is required. No schema is needed, the application creates that itself the first time it runs.
* We recommend using a cloud with managed Docker services, e.g. ECS on AWS, or Kubernetes.

## Multiple Application

This is the default option. You deploy the standard Docker image, you don't need to build your own.

The Service Portal is part of the installation:

* Applications are published from Git using the Service Portal.
* Application files are not stored inside the Docker image.

## Single Application

This is a special option.

With this deployment mode the application directory is stored directly inside the Docker image. You build your own Docker image with your own Application files being part of that container.

Hence, only a single application will be available with this installation.

{% hint style="warning" %}
#### No facility for publishing!

Note that the Service Portal will not be available with this deployment mode. There is no facility for publishing! On every change of your configuration you will have to build a new Docker container!
{% endhint %}
