# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
  config.vm.hostname = "endpoints"
  config.vm.box = "ubuntu/jammy64"   # 22.04 (we use Docker for deployment so version here doesn't really matter)
  config.vm.network "forwarded_port", guest: 8080, host: 9758   # jetty or docker
  config.vm.network "forwarded_port", guest: 9999, host: 9759   # Java debugging
  config.vm.network "forwarded_port", guest: 5432, host: 9760   # PostgreSQL
  config.vm.network "forwarded_port", guest: 4566, host: 4566   # AWS S3 emulation

  if not Vagrant::Util::Platform.windows? then
    config.vm.synced_folder "~/.m2", "/home/vagrant/.m2"
  end

  config.vm.provider "virtualbox" do |vb|
    vb.memory = 4000
    vb.cpus = 2           # On Adrian's MacBook Pro 13" 2015, Git 394be, 1m30s with 2c, 1m50s with 4c, and 2c uses less battery
  end
  
  # runs as root within the VM
  config.vm.provision "shell", inline: %q{
  
    set -e  # stop on error

    echo --- General OS installation
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get upgrade -qy    # grub upgrade warnings mess with the terminal
    apt-get -qy install vim ntp unattended-upgrades less openjdk-21-jdk libxml2-utils jq

    echo -- PostgreSQL
    apt-get -qy install postgresql-14
    echo "host all all 0.0.0.0/0 md5" >> /etc/postgresql/14/main/pg_hba.conf
    sed -i 's/^#listen_addresses = .*$/listen_addresses = '"'"'*'"'"'/' /etc/postgresql/14/main/postgresql.conf
    /etc/init.d/postgresql restart
    (cd /tmp && sudo -u postgres psql -c "alter user postgres password 'postgres'" postgres)  # os user postgres cannot see /root dir

    echo --- MySQL
    apt-get install -qy mysql-server mysql-client
    echo "bind-address = 0.0.0.0" >> /etc/mysql/mysql.conf.d/mysqld.cnf
    mysql -e 'CREATE USER '"'"'root'"'"'@'"'"'%'"'"' IDENTIFIED BY '"'"'root'"'"''
    mysql -e 'GRANT ALL ON *.* TO '"'"'root'"'"'@'"'"'%'"'"''
    mysql -e "UPDATE mysql.user SET authentication_string=null WHERE User='root';"
    mysql -e 'FLUSH PRIVILEGES'
    mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root';"
    /etc/init.d/mysql restart
    echo 'mysql -uroot -proot example_application' >> ~vagrant/.bash_history

    echo --- Set DeploymentParameters
    echo 'export AWS_REGION=us-east-1' >> /etc/environment   # for CloudWatch APIs
    echo 'export ENDPOINTS_BASE_URL=http://localhost:9758/' >> /etc/environment
    echo 'export ENDPOINTS_JDBC_URL='"'"'jdbc:postgresql://localhost/endpoints?user=postgres&password=postgres'"'" >> /etc/environment
    echo 'export ENDPOINTS_AWS_S3_ENDPOINT_OVERRIDE=http://s3.localhost.localstack.cloud:4566/' >> /etc/environment
    echo 'export ENDPOINTS_AWS_SECRETS_MANAGER_ENDPOINT_OVERRIDE=http://secretsmanager.us-east-1.localhost.localstack.cloud:4566/' >> /etc/environment
    echo 'export ENDPOINTS_AWS_CLOUDWATCH_ENDPOINT_OVERRIDE=http://cloudwatch.us-east-1.localhost.localstack.cloud:4566/' >> /etc/environment
    echo 'export ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY=/var/endpoints/applications-checkout' >> /etc/environment
    echo 'export ENDPOINTS_DISPLAY_EXPECTED_HASH=true' >> /etc/environment
    echo 'export ENDPOINTS_XSLT_DEBUG_LOG=true' >> /etc/environment
    echo 'export ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME=Vagrant' >> /etc/environment
    echo 'export ENDPOINTS_AWS_CLOUDWATCH_METRICS_INSTANCE=Vagrant' >> /etc/environment
    echo 'export ENDPOINTS_SINGLE_APPLICATION_MODE_TIMEZONE_ID=UTC' >> /etc/environment
    echo 'export EXAMPLE_APPLICATION_POSTGRESQL_JDBC='"'"'jdbc:postgresql://localhost/example_application?user=postgres&password=postgres'"'" >> /etc/environment
    echo 'export EXAMPLE_APPLICATION_MYSQL_JDBC='"'"'jdbc:mysql://localhost/example_application?user=root&password=root&useUnicode=true&characterEncoding=UTF-8'"'" >> /etc/environment
    source /etc/environment

    echo --- Set DeploymentParameters for Docker
    echo 'AWS_REGION=us-east-1' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_BASE_URL=http://localhost:9758/' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_JDBC_URL=jdbc:postgresql://localhost/endpoints?user=postgres&password=postgres' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_AWS_S3_ENDPOINT_OVERRIDE=http://s3.localhost.localstack.cloud:4566/' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_AWS_SECRETS_MANAGER_ENDPOINT_OVERRIDE=http://secretsmanager.us-east-1.localhost.localstack.cloud:4566/' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_AWS_CLOUDWATCH_ENDPOINT_OVERRIDE=http://cloudwatch.us-east-1.localhost.localstack.cloud:4566/' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_DISPLAY_EXPECTED_HASH=true' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_XSLT_DEBUG_LOG=true' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME=Docker in Vagrant' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_AWS_CLOUDWATCH_METRICS_INSTANCE=Docker in Vagrant' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_SINGLE_APPLICATION_MODE_TIMEZONE_ID=UTC' >> /home/vagrant/docker-env
    echo 'EXAMPLE_APPLICATION_POSTGRESQL_JDBC=jdbc:postgresql://localhost/example_application?user=postgres&password=postgres' >> /home/vagrant/docker-env
    echo 'EXAMPLE_APPLICATION_MYSQL_JDBC=jdbc:mysql://localhost/example_application?user=root&password=root&useUnicode=true&characterEncoding=UTF-8' >> /home/vagrant/docker-env

    echo --- /var dirs
    mkdir -p -m 0777 /var/endpoints/applications-checkout

    echo --- Create endpoints PostgreSQL database
    (cd /tmp && sudo -u postgres psql -c "create database endpoints")
    echo 'sudo -u postgres psql -c "drop database endpoints" && sudo -u postgres psql -c "create database endpoints"' >> ~vagrant/.bash_history
    echo 'psql -hlocalhost endpoints postgres' >> ~vagrant/.bash_history
    echo localhost:5432:endpoints:postgres:postgres >> ~vagrant/.pgpass && chown vagrant ~vagrant/.pgpass && chmod go-rwX ~vagrant/.pgpass

    echo --- Install Maven
    apt-get -qy install maven
    echo 'export MAVEN_OPTS="-ea -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=0.0.0.0:9999"' >> /etc/environment
    
    echo --- Install docker
    apt-get install -qy apt-transport-https ca-certificates curl gnupg2 software-properties-common
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt-get update
    apt-get install -qy docker-ce

    echo --- Install localstack, which simulates a lot of AWS services
    docker run -p 4566:4566 -p 4510-4559:4510-4559 -d --name=aws --restart unless-stopped localstack/localstack:3.1.0
    cat << '    END' >> ~vagrant/.bash_aliases
       alias list-localstack-aws-s3-files="curl http://vagrantbucket.s3.localhost.localstack.cloud:4566/ | xmllint --format -"
       alias list-localstack-aws-secrets="curl -H 'Content-Type: application/x-amz-json-1.1' -H 'X-Amz-Target: secretsmanager.ListSecrets' -d '{}' 'http://secretsmanager.us-east-1.localhost.localstack.cloud:4566/'| jq" 
    END
    
    echo --- Install AWS CLI client for accessing local simulation
    apt-get install -qy python3-pip unzip
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    unzip awscliv2.zip
    ./aws/install
    pip install awscli-local

    echo --- Build software
    sudo -u vagrant /bin/bash -c 'source /etc/environment && mvn -f /vagrant/pom.xml clean test'
    echo 'mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true clean jetty:run' >> ~vagrant/.bash_history

    echo --- Create example-application PostgreSQL schema
    (cd /tmp && sudo -u postgres psql -c "create database example_application")
    (cd /tmp && sudo -u postgres psql example_application -c "create table data_source (i integer, l bigint, u uuid, str varchar)")
    (cd /tmp && sudo -u postgres psql example_application -c "insert into data_source values (2, 2, 'd1fd12ff-5628-4857-868b-a6004f119c47', 'foo')")
    echo localhost:5432:example_application:postgres:postgres >> ~vagrant/.pgpass && chown vagrant ~vagrant/.pgpass && chmod go-rwX ~vagrant/.pgpass

    echo --- Create example-application MySQL schema
    mysql -uroot -proot -e 'CREATE DATABASE example_application'
    mysql -uroot -proot example_application -e 'CREATE TABLE log_to_database (invoice_number VARCHAR(100), `withouthyphen` VARCHAR(100))'
    mysql -uroot -proot example_application -e 'CREATE TABLE data_source (i INTEGER, str VARCHAR(100))'
    mysql -uroot -proot example_application -e 'INSERT INTO data_source VALUES (2, "foo")'

    echo '--- Set up "example-application" and Service portal access'
    ln -s /vagrant/example-application /var/endpoints/applications-checkout/example-application-symlink
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO application_config(application_name, display_name, git_url) VALUES 
        ('example-application', 'Example Application', 'invalid'),
        ('from-git',            'From Git',            '/var/endpoints/application-git-repositories/from-git')")
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO application_publish VALUES ('example-application', 'symlink', 'live')")
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO service_portal_login_application VALUES ('admin', 'example-application'), ('admin', 'from-git')")

    echo '--- Create Git repository, to publish from'
    apt-get -qy install git
    mkdir -p -m 0777 /var/endpoints/application-git-repositories
    git init --bare /var/endpoints/application-git-repositories/from-git
    cp -r /vagrant/example-application /tmp/
    (cd /tmp/example-application &&
       git init &&
       git remote add origin /var/endpoints/application-git-repositories/from-git &&
       git add . &&
       git commit -m "Initial commit" &&
       git push origin master:master)
    chmod -R a+rwX /var/endpoints/application-git-repositories

    echo --- Install HTTP listener to test certain requests
    docker run -p 8081:8080 -d --name=http-server --restart unless-stopped mendhak/http-https-echo
  }
  
  config.vm.provision "shell", run: "always", inline: %q{
  
    set -e  # stop on error
  
    echo --- Wait for localstack to start
    while ! curl -f http://s3.localhost.localstack.cloud:4566/ ; do echo Waiting for localstack to start...; sleep 1; done 

    echo --- Create our S3 data in localstack for testing
    curl --fail-with-body -X PUT 'http://vagrantbucket.s3.localhost.localstack.cloud:4566/'
    echo '<data-in-s3/>' | curl --fail-with-body -X PUT -T - 'http://vagrantbucket.s3.localhost.localstack.cloud:4566/file-in-s3.xml'
  
    echo --- Create secrets in Secrets Manager of localstack for testing
    curl --fail-with-body -H 'Content-Type: application/x-amz-json-1.1' -H 'X-Amz-Target: secretsmanager.CreateSecret' -d '{"ClientRequestToken":"70b1d1e6-8b36-4556-95ab-bd472cf893d7","Name":"VagrantSecret","SecretString":"MySecretValue"}' 'http://secretsmanager.us-east-1.localhost.localstack.cloud:4566/'
  
    echo ''
    echo '-----------------------------------------------------------------'
    echo 'After "vagrant ssh", use:'
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true clean jetty:run '
    echo '    Then surf to: '
    echo '       http://localhost:9758/example-application/json?param-in-hash=x&email-address=y&email-address=email-address&hash=95be18ae19a5ac3c67d1db62826eed239615563d28e46f87c61bd609b12a1f5f&debug=true '
    echo '       http://localhost:9758/service-portal (admin/admin)'
    echo '    Or, after configuring a mail server on port 25 in the Vagrant VM: '
    echo '       http://localhost:9758/example-application/pdf?param-in-hash=x&email-address=adrian.m.smith@gmail.com&hash=f4eb5eab9373d389985279e4e6f9b737fe760d6c40b53aedf72cc00e36886629&debug=true '
    echo '      Or open send-email-test.html '
    echo '    Or execute: '
    echo '       curl -d "<parameter name=\"param-in-hash\" value=\"x\"/>" \'
    echo '           -H "Content-Type: application/xml"                    \'
    echo '           "http://localhost:9758/example-application/json?email-address=y,email-address&hash=95be18ae19a5ac3c67d1db62826eed239615563d28e46f87c61bd609b12a1f5f" '
    echo '       curl -d "{ 'foo': 'bar' }" \'
    echo '           -H "Content-Type: application/json"                    \'
    echo '           "http://localhost:9758/example-application/json?email-address=y,email-address&hash=95be18ae19a5ac3c67d1db62826eed239615563d28e46f87c61bd609b12a1f5f" '
    echo '  psql -hlocalhost endpoints postgres '
    echo '  psql -hlocalhost example_application postgres '
    echo '  mysql -uroot -proot example_application'
    echo '  list-localstack-aws-s3-files '
    echo '  list-localstack-aws-secrets '
    echo '  awslocal cloudwatch list-metrics '
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true package \'
    echo '      && sudo docker build -t endpoints /vagrant \'
    echo '      && sudo docker run -i -t --env-file ~/docker-env \'
    echo '          --mount type=bind,source=/vagrant/example-application,target=/tmp/example-application-symlink \'
    echo '          --net=host endpoints'
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true package \'
    echo '      && sudo docker build -t endpoints /vagrant \'
    echo '      && sudo docker run -i -t --env-file ~/docker-env \'
    echo '          --mount type=bind,source=/vagrant/example-application,target=/var/endpoints/fixed-application \'
    echo '          --net=host endpoints'
    echo '  mvn -f /vagrant/pom.xml clean package                    \'
    echo '      && sudo docker build --pull -t offerready/endpoints-he /vagrant \'
    echo '      && sudo docker push offerready/endpoints-he                     '
    echo '-----------------------------------------------------------------'
    echo ''
  }
  
end
