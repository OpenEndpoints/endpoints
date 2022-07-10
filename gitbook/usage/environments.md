# Environments

The current configuration of an application is loaded from a GIT to OpenEndpoints (with the "Publisher" in the service portal).

To enable a simple form of "staging", 2 different versions of the GIT repository can be activated at the same time. There are 2 different "environments" for this: PREVIEW and LIVE.

## Publisher

The "Publisher" in the service portal lets you

1. Publish the latest version of the GIT repository to the PREVIEW environment.
2. Promote the current version from PREVIEW to LIVE.

![environments image](https://cdn.openendpoints.io/images/gitbook/usage-environments.png)

## Usage

By default, all requests are always sent to the LIVE environment. To send a request to the PREVIEW environment, an additional parameter must be added:

{% hint style="info" %}
#### Parameter to send a request to the PREVIEW environment

**environment=preview**
{% endhint %}

It is not required to add a parameter for the LIVE environment.

{% hint style="warning" %}
#### Hash calculation

Note that the hash calculation takes the parameter for the environment into account. There are therefore 2 different hash values for the two environments. - see: **Authentication**.
{% endhint %}
