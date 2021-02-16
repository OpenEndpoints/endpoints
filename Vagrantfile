# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
  config.vm.hostname = "endpoints"
  config.vm.box = "ubuntu/bionic64"   # 18.04, We only support deployment via Docker 
  config.vm.network "forwarded_port", guest: 8080, host: 9758   # jetty or docker
  config.vm.network "forwarded_port", guest: 9999, host: 9759   # Java debugging
  config.vm.network "forwarded_port", guest: 5432, host: 9760   # PostgreSQL

  if not Vagrant::Util::Platform.windows? then
    config.vm.synced_folder "~/.m2", "/home/vagrant/.m2"
  end

  config.vm.provider "virtualbox" do |vb|
    vb.memory = 2000      # On Adrian's iMac Pro, increasing memory had little effect on speed
    vb.cpus = 2           # On Adrian's MacBook Pro 13" 2015, Git 394be, 1m30s with 2c, 1m50s with 4c, and 2c uses less battery
  end
  
  # runs as root within the VM
  config.vm.provision "shell", inline: %q{
  
    set -e  # stop on error

    echo --- General OS installation
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get upgrade -qy    # grub upgrade warnings mess with the terminal
    apt-get -qy install vim ntp unattended-upgrades less

    echo --- Install Java 11 \(OpenJDK\)
    wget -q https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz -O /tmp/openjdk-11.tar.gz
    mkdir /usr/lib/jvm
    tar xfvz /tmp/openjdk-11.tar.gz --directory /usr/lib/jvm
    rm -f /tmp/openjdk-11.tar.gz
    for bin in /usr/lib/jvm/jdk-11.0.2/bin/*; do update-alternatives --install /usr/bin/$(basename $bin) $(basename $bin) $bin 100; done
    for bin in /usr/lib/jvm/jdk-11.0.2/bin/*; do update-alternatives --set $(basename $bin) $bin; done

    echo -- PostgreSQL
    apt-get -qy install postgresql-10
    echo "host all all 0.0.0.0/0 md5" >> /etc/postgresql/10/main/pg_hba.conf
    sed -i 's/^#listen_addresses = .*$/listen_addresses = '"'"'*'"'"'/' /etc/postgresql/10/main/postgresql.conf
    /etc/init.d/postgresql restart
    (cd /tmp && sudo -u postgres psql -c "alter user postgres password 'postgres'" postgres)  # os user postgres cannot see /root dir

    echo --- MySQL
    apt-get install -qy mysql-server mysql-client
    echo "bind-address = 0.0.0.0" >> /etc/mysql/mysql.conf.d/mysqld.cnf
    mysql -e 'CREATE USER '"'"'root'"'"'@'"'"'%'"'"' IDENTIFIED BY '"'"'root'"'"''
    mysql -e 'GRANT ALL ON *.* TO '"'"'root'"'"'@'"'"'%'"'"''
    mysql -e 'UPDATE mysql.user SET plugin="mysql_native_password" WHERE User="root"'
    mysql -e "UPDATE mysql.user SET authentication_string=PASSWORD('root')  WHERE  User='root';"
    mysql -e 'FLUSH PRIVILEGES'
    /etc/init.d/mysql restart
    echo 'mysql -uroot -proot example_application' >> ~vagrant/.bash_history

    echo --- Set DeploymentParameters
    echo 'export ENDPOINTS_BASE_URL=http://localhost:9758/' >> /etc/environment
    echo 'export ENDPOINTS_JDBC_URL='"'"'jdbc:postgresql://localhost/endpoints?user=postgres&password=postgres'"'" >> /etc/environment
    echo 'export ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY=/var/endpoints/applications-checkout' >> /etc/environment
    echo 'export ENDPOINTS_DISPLAY_EXPECTED_HASH=true' >> /etc/environment
    echo 'export ENDPOINTS_XSLT_DEBUG_LOG=true' >> /etc/environment
    echo 'export ENDPOINTS_GIT_REPOSITORY_DEFAULT_PATTERN=/var/endpoints/application-git-repositories/${applicationName}' >> /etc/environment
    echo 'export ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME='"'"'VAGRANT ENVIRONMENT'"'" >> /etc/environment
    echo 'export EXAMPLE_APPLICATION_POSTGRESQL_JDBC='"'"'jdbc:postgresql://localhost/example_application?user=postgres&password=postgres'"'" >> /etc/environment
    echo 'export EXAMPLE_APPLICATION_MYSQL_JDBC='"'"'jdbc:mysql://localhost/example_application?user=root&password=root&useUnicode=true&characterEncoding=UTF-8'"'" >> /etc/environment
    source /etc/environment

    echo --- Set DeploymentParameters for Docker
    echo 'ENDPOINTS_BASE_URL=http://localhost:9758/' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_JDBC_URL=jdbc:postgresql://localhost/endpoints?user=postgres&password=postgres' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_PUBLISHED_APPLICATION_DIRECTORY=/var/endpoints/applications-checkout' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_DISPLAY_EXPECTED_HASH=true' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_XSLT_DEBUG_LOG=true' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_GIT_REPOSITORY_DEFAULT_PATTERN=/var/endpoints/application-git-repositories/${applicationName}' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_SERVICE_PORTAL_ENVIRONMENT_DISPLAY_NAME=VAGRANT ENVIRONMENT (DOCKER)' >> /home/vagrant/docker-env
    echo 'EXAMPLE_APPLICATION_POSTGRESQL_JDBC=jdbc:postgresql://localhost/example_application?user=postgres&password=postgres' >> /home/vagrant/docker-env
    echo 'EXAMPLE_APPLICATION_MYSQL_JDBC=jdbc:mysql://localhost/example_application?user=root&password=root&useUnicode=true&characterEncoding=UTF-8' >> /home/vagrant/docker-env
    echo 'ENDPOINTS_SINGLE_APPLICATION_MODE_TIMEZONE_ID=Europe/Vienna'

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

    echo --- Build software
    sudo -u vagrant /bin/bash -c 'source /etc/environment && mvn -f /vagrant/pom.xml clean test'
    echo 'mvn -f /vagrant/pom.xml -Dspotbugs.skip=true jetty:run' >> ~vagrant/.bash_history

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
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO application_config VALUES ('example-application', false, 'Example Application'), ('from-git', false, 'From Git')")
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO application_publish VALUES ('example-application', 'symlink', 'live')")
    (cd /tmp && sudo -u postgres psql endpoints -c "INSERT INTO service_portal_login VALUES ('admin', '\$2a\$10\$VOBc53Fu0louc.K4AGLUJuiTdbPimluy4feYeShLIrOrQy//U.UpO')")
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

    echo --- Install docker
    apt-get install -qy apt-transport-https ca-certificates curl gnupg2 software-properties-common
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt-get update
    apt-get install -qy docker-ce

    echo --- Install HTTP listener to test certain requests
    docker run -p 8081:80 -d --name=http-server --restart unless-stopped -t mendhak/http-https-echo
  }
  
  config.vm.provision "shell", run: "always", inline: %q{
  
    set -e  # stop on error
    
    echo ''
    echo '-----------------------------------------------------------------'
    echo 'After "vagrant ssh", use:'
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true jetty:run '
    echo '    Then surf to: '
    echo '       http://localhost:9758/example-application/json?param-in-hash=x&email-address=y&email-address=email-address&hash=95be18ae19a5ac3c67d1db62826eed239615563d28e46f87c61bd609b12a1f5f&debug=true '
    echo '       http://localhost:9758/service-portal (admin/admin)'
    echo '    Or open send-email-test.html '
    echo '    Or execute: '
    echo '       curl -d "<parameter name=\"param-in-hash\" value=\"x\"/>" \'
    echo '           -H "Content-Type: application/xml"                    \'
    echo '           http://localhost:9758/example-application/json?hash=6b909a212d885c8eba7cf62e0efb01067a52dd2e53b4fd4895408fc0abd7527e '
    echo '  psql -hlocalhost endpoints postgres '
    echo '  psql -hlocalhost example_application postgres '
    echo '  mysql -uroot -proot example_application'
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true package \'
    echo '      && sudo docker build -t endpoints /vagrant \'
    echo '      && sudo docker run -i -t --env-file ~/docker-env \'
    echo '          --mount type=bind,source=/vagrant/example-application,target=/var/endpoints/applications-checkout/example-application-symlink \'
    echo '          --net=host endpoints'
    echo '  mvn -f /vagrant/pom.xml -DSaxon=PE -Dspotbugs.skip=true package \'
    echo '      && sudo docker build -t endpoints /vagrant \'
    echo '      && sudo docker run -i -t --env-file ~/docker-env \'
    echo '          -e ENDPOINTS_GIT_REPOSITORY_DEFAULT_PATTERN= \'
    echo '          --mount type=bind,source=/vagrant/example-application,target=/var/endpoints/fixed-application \'
    echo '          --net=host endpoints'
    echo '  mvn -f /vagrant/pom.xml clean package                    \'
    echo '      && sudo docker build --pull -t offerready/endpoints-he /vagrant \'
    echo '      && sudo docker push offerready/endpoints-he                     '
    echo '-----------------------------------------------------------------'
    echo ''
  }
  
end
