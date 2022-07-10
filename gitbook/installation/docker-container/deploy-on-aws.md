# Deploy on AWS

## Get Access to an AWS Account

Firstly you need to either create an AWS account, or you need to have access to an AWS account.

## Decide on Domain

Decide on what internet domain the new Endpoints deployment should be reachable at. For example `https://endpoints.myservice.com/`.

This can either be a domain such as `myservice.com` or a subdomain such as `endpoints.myservice.com`. In either case, you must own the main domain you wish to host on (`myservice.com` in this example). Purchasing this domain is out-of-scope of this document.

## Apply for an HTTPS Certificate via AWS

AWS offers free HTTPS certificates for use with their own services.

1. In the AWS Management Console (web interface), navigate to the Certificate Manager product.
2. Click “Request a Certificate”
3. On the form that is displayed, select “Request a Public Certificate” as this will be a publicly visible service.
4. Type in the domain name of the certificate; this is the full domain name, from the example above this would be `endpoints.myservice.com` There is no https:// part or trailing slash or dot.
5. Click on the “Review and Request” button, then the “Confirm and Request” button.
6. This will either send an email to the owner of the domain (containing a link), or it will request that a particular DNS entry is made in the DNS settings for that domain. This is to prove that you own the domain that AWS will create the HTTPS certificate for. Either click on the link in the email or set up the DNS entry. After having set up the DNS entry, a few minutes later the screen on the AWS Management Console will show the certificate has been issued.

## Create VPC and Subnets

A VPC is a part of the network managed by AWS. If this AWS account will do nothing more than serve Endpoints, you can use the default VPC. If it is shared with other services, for security we recommended creating a separate VPC for the Endpoints installation. If you decide to use a default VPC, you can skip this section. If you decide to use a separate VPC, follow the following steps to create it:

1. In the AWS Management Console (web interface), navigate to the VPC product.
2. Create a new VPC, with the appropriate IP address range, such as 10.100.0.0/16. (Ignore the options about IPv6.) You can use any internal IP address range you like, as long as it doesn't conflict with any other internal IP addresses that the VPC may need to have access to (e.g. if Endpoints needs to be configured to access any databases or services internal to your company). If in doubt, talk to the department which manages IP addresses in your company. If the VPC does not need to access any other internal services, there is no reason not to proceed with the example given here.
3. Create two Subnets, with IP address schemes like 10.100.10.0/26 and 10.100.10.64/26. Allow “Specify the Availability Zones” explicitly, choose different zones for each Subnet, otherwise they all get created in the same Availability Zones. This allows services to be split across multiple Availability Zones, meaning if one AWS data center encounters issues, then the application will still be available.
4. Create an “Internet Gateway”. This will allow the VPC to send and receive requests from the internet.
   1. After the "Internet Gateway" has been created, select it, use the "Actions" drop-down to select "Attach to VPC" and select the VPC you created earlier.
5. Create a “Route Table”, then after its creation:
   1. Click on the “Routes” tab at the bottom half of the screen. Add a rule from Destination 0.0.0.0/0 to the new Internet Gateway created above.
   2. Click on “Subnet Association” tab and associate all the subnets with the routing table.

## Create Security Groups

It is a good idea to create all security group in advance. Security groups can also be created when databases and other resources are created, however then they have useless names such as “rds-launch-wizard-2”.

On the AWS Management Console (web interface), navigate to the Security Group feature. The following table specifies which security groups to create. Each have a name and one or more inbound rules. For outbound rules, allow the default which is to be able to send requests on any port to any location.

| Security Group      | Inbound Rules                                                                  |
| ------------------- | ------------------------------------------------------------------------------ |
| Load Balancer HTTPS | HTTPS / Source Anywhere IPv4                                                   |
| WebApp              | All TCP / Source “Load Balancer HTTPS”                                         |
| SSH from Internet   | SSH / Source Anywhere IPv4                                                     |
| DB                  | <p>PostgreSQL / Source “WebApp”<br>PostgreSQL / Source “SSH from Internet”</p> |

## Create "Bastion" VM

For various tasks, it is useful to have a VM to connect to via SSH. This is "behind the firewall" and will allow you to access resources such as the database which are not public.

1. In the AWS Management Console (web interface), navigate to EC2 product.
2. If you do not already have an SSH public key then go to the left navigation under "Key Pairs" and create a new Key Pair. If you already have an SSH public key, e.g. created on your computer, you do not need to do this step, you can upload it later.
3. Navigate to the "Instances" section of the left-hand navigation.
4. Click “Create New VM”
5. Select the latest Ubuntu image
6. Select a very small instance size (to save costs)
7. Select the VPC created earlier
8. Set “Auto-assign public IP” to Enable
9. Set the tag “Name” to e.g. “SSH from Internet”
10. Select the “SSH from Internet” Security Group created earlier
11. Select an SSH Key pair that you have access to so that you can log on to the server.

It takes quite a while before a user can log in to a newly created EC2 instance, for example 5 minutes. You might see the error "Permission denied (publickey)" during this time.

To connect to it, use the ssh username `ubuntu` together with the key you created or uploaded earlier.

## Create PostgreSQL Database

Endpoints uses the database to store various things such as which Endpoints "applications" have been installed.

1. In the AWS Management Console (web interface), navigate to RDS product. This is the AWS managed database product.
2. Click “Create a new database”.
3. Name it something like “Endpoints”
4. We currently support PostgreSQL 10.
5. Select a random master password.
6. Select “Create new Subnet Group”
7. Set “Public Access” to “No”, as this database should not be publicly visible on the internet.
8. Select the database Security Group that was created earlier.
9. Database schema creation is not necessary; software does that on startup.
10. “Point-in-Time Recovery” is activated by default, so no action is required to enable that.
11. Make sure that “Auto Minor Version Upgrade” is enabled, so that you do not have to take care of manually upgrading the database between minor versions.

## Create the Task Definition

A Task Definition is a blueprint of how AWS will install and run the Endpoints software.

1. In the AWS Management Console (web interface), navigate to the ECS product. ECS is the service AWS offers to manage Docker installations.
2. Create a new Task Definition
3. Select type "Fargate". This means that AWS itself will automatically allocate compute resources, as opposed to having to do it manually.
4. Name the Task Definition with a name like "Endpoints"
5. Select the RAM and CPU. We recommend at least 500MB RAM.
6. Add one container within the Task Definition. Give it a name like "endpoints".
7. The URL to the public Docker image of Endpoints is `public.ecr.aws/x1t6d0t7/endpoints-he`
8. Set a hard memory limit e.g. 450MB RAM.
9. Add a single port mapping to the container. Container port is 8080.
10. Add environment variables. This is just the minimal set to start working, there are more options, see [Docker Environment Variables](docker-environment-variables.md) for more details for more details.
    1. `ENDPOINTS_JDBC_URL` is like `jdbc:postgresql://xxxxx/postgres?user=postgres&password=postgres` where xxxx is the host value of the RDS database
    2. `ENDPOINTS_BASE_URL` is the URL of the service e.g. `https://endpoints.myservice.com/` with a trailing slash
    3. `ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME` For example “live environment”. This is just text which is displayed on the Service Portal login page. In case you have multiple Endpoints installations, it is convenient to differentiate them with text on the login page.

## Create an ECS Cluster

A "Cluster" is a set of compute resources, managed by AWS, where Endpoints will run.

1. In the AWS Management Console (web interface), navigate to the ECS product
2. Create the ECS cluster (not Kubernetes Cluster)
3. Select “Networking only” type.
4. Don’t select creation of a new VPC

## Create a Load Balancer (ELB)

The Load Balancer is responsible for taking the user's HTTPS requests, and forwarding them on to the Endpoints software running on a managed ECS cluster created above.

1. In the AWS Management Console (web interface), navigate to the ECS product
2. Go to the “Load Balancers” section.
3. Click “Create Load Balancer”.
4. Select the default “Application Load Balancer” from the two options.
5. Change the listener to be HTTPS only.
6. Select the correct VPC, which was created above.
7. Select all subnets.
8. Select the HTTPS certificate that has been previously created.
9. Select the HTTPS security group previously created.
10. Go to the "Load balancer target group".
    1. Create a new Target Group. However, its settings are not important, later it will be deleted, as each time a Docker instance is registered with it, it will create a new Target Group.
    2. Do not register any targets to the newly created Target Group (as it will be deleted later)

## Connect the Domain to the Load Balancer

This is necessary so that when someone navigates to your domain, their requests are sent to the AWS Load Balancer created above, and thus the request can be served by Endpoints.

1. In the AWS Management Console (web interface), go to the EC2 product.
2. In the Load Balancer section, click on the Load Balancer created above. You will see it has a DNS name such as `Endpoints-HTTPS-66055465.eu-central-1.elb.amazonaws.com` (A Record).
3. In the tool where you administer the domain, create a CNAME DNS record, from the domain or subdomain chosen for this installation, to the domain name you can read off in the last step.

## Add Services to the Cluster

This step takes the Task Definition you have created earlier (which is a blueprint for running the software) and installs it on the Cluster created earlier (a set of compute resources).

1. In the AWS Management Console (web interface), go to the ECS product.
2. Navigate to ECS “Clusters” (not Kubernetes Clusters)
3. Select the newly created “Cluster”.
4. Create an service called “Endpoints”
5. Type is Fargate
6. select the Task Definition created above
7. Select the VPC created above
8. Add all subnets
9. Select the “webapp” security group, created above
10. In the Load Balancing section:
    1. Select “Application Load Balancer”
    2. Select the load balancer previously created in the drop-down
    3. Click the “Add to load balancer” button
    4. Select the target group name
    5. Select the existing “production listener”
    6. The URL is `/*` i.e. slash followed by a star
    7. Health check is `/health-check`
11. Set the application as "sticky" in the load balancer: (This is required for the Service Portal inc case more than one instance is running as the "state" of the web application is stored in the server memory)
    1. Navigate to the EC2 Product in the Management Interface.
    2. Go to the Target Group section in the left navigation
    3. Select the previously-created Target Group
    4. Navigate to the "Attributes" tab
    5. Click "Edit"
    6. Enable the "Stickiness" checkbox
    7. Select the "Load balancer generated cookie" option.

## Create Monitoring Alarms

It is possible, but not necessary, to use CloudWatch to create monitoring alerts for the health of various components such as the database. To create an alarm:

1. In the AWS Management Console (web interface), go to the CloudWatch product.
2. Click “Create Alarm”.
3. Set up the alarm, as described below
4. On the last screen, select that the action should be to send an email to the appropriate person.

Perform the above steps for each of the following alarms:

* **CPU:** ECS -> CPUUtilization, > 70 (percentage) for 1 period (= 5 minutes)
* **Memory:** ECS -> MemoryUtilization > 70 (percentage) for 1 period (= 5 minutes)
* **Up:** ApplicationELB > Per AppELB, per TG Metrics -> UnHealthyHostCount, > 0 for 1 period (= 5 minutes)
* **DB Disk:** RDB -> Per-Database Metrics -> FreeStorageSpace for digitalservices < 5 (percentage) for 1 datapoint

## Use Application

The application is now available under your URL e.g. `https://endpoints.myservice.com/`

If the application is configured in multi-application mode then the Service Portal is available under `https://endpoints.myservice.com/service-portal` with username admin/admin.

## Troubleshooting

* Go to the ECS Cluster, go to the Service, click on the “Events” tab to see what’s going on, e.g. health check is failing
* Go to CloudWatch, Log Groups, see that there is a new log group which has been created, go into the log file and see if there are any errors.
