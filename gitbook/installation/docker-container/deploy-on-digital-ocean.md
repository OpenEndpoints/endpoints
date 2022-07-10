# Deploy on Digital Ocean

This is an **example installation** on [DigitalOcean](https://digitalocean.com). For sure there are many more paths how-to install OpenEndpoints on DigitalOcean.

1. Sign up to DigitalOcean or be invited to an existing account
2. On the left hand navigation go to “API” and “Generate New Token” with a name such as your name. Record the token somewhere for later.
3. Create a Project
   1. Top-left of the navigation all projects are listed
   2. Click on “New Project”
   3. Enter the name “Endpoints”
   4. For “What’s it for” answer “Operational / Developer tooling”
   5. For “Environment” answer “Production”
4. Upload the Endpoints software as a Docker image to DigitalOcean Docker Repository
   1. Go to “Container Registry” on the left hand side navigation
   2. Use a name which is unique to your company or product; this is globally unique across all of DigitalOcean. We use “endpoints” in this example. Highlighted in green so that you know where you need to replace it in the later steps.
   3. `wget ``https://github.com/digitalocean/doctl/releases/download/v1.17.0/doctl-1.17.0-linux-amd64.tar.gz` See bug [https://stackoverflow.com/a/60237742/220627](https://stackoverflow.com/a/60237742/220627)
   4. `curl -sL https://github.com/digitalocean/doctl/releases/download/v1.38.0/doctl-1.38.0-linux-amd64.tar.gz | tar -xzv`
   5. `sudo mv ~/doctl /usr/local/bin`
   6. `mkdir ~/.config` # see bug [https://github.com/digitalocean/doctl/issues/591](https://github.com/digitalocean/doctl/issues/591)
   7. `doctl auth init`
   8. `sudo doctl registry login`
   9. `sudo docker pull public.ecr.aws/x1t6d0t7/endpoints-he`
   10. `sudo docker tag public.ecr.aws/x1t6d0t7/endpoints-he registry.digitalocean.com/<your-name>`
   11. `sudo docker push registry.digitalocean.com/<your-name>`
5. Create a database:
   1. Click on “Databases” in left navigation
   2. Choose “PostgreSQL”
   3. Currently we recommend PostgreSQL 10+.
   4. We recommend starting with the cheapest version which is “Basic Node” with 1GB of RAM.
   5. Choose your Data Center.
   6. Under “Choose a unique database cluster name” choose something like “endpoints”.
   7. Click the green “Create a Database Cluster” at the bottom of the screen to actually start the creation process. (The creation process takes a while, e.g. 5-10 minutes.)
   8. After you start the creation process, the resulting screen displays information about the database, with a few tabs such as “Overview”, “Insights” etc.
6. Create the application:
   1. On the left navigation click on “Apps”
   2. Create a new app
   3. Select the 4th option (without the icon) “DigitalOcean Container Registry”
   4. Add the following environment variables:
      1. `JAVA_OPTIONS=-verbose:gc`
      2. `ENDPOINTS_BASE_URL`- Use whatever domain you want the service to be running under e.g. https://endpoints.mycompany.com/
      3. `ENDPOINTS_JDBC_URL`
         1. Go to the database settings screen
         2. Go to the bottom right of the screen “Connection Details”
         3. Select the default “Public Network”
         4. Use a format like `jdbc:postgresql://<host>:<port>/<database>?user=<username>&password=<password>`
      4. `ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME=DigitalOcean`
   5. Choose an app name, this is used in the internal URL but otherwise doesn’t matter much
   6. Wait for it to deploy
   7. See the URL and check it works
   8. Enter a CNAME in your DNS from the URL you want to the one that DigitalOcean has supplied
   9. Go to Settings, in “App Settings” under “Domains” add the domain you want the service to run under, so that HTTPS certificates work.
