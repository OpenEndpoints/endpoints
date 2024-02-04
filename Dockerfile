FROM jetty:9.4-jre17
COPY target/endpoints /var/lib/jetty/webapps/ROOT
EXPOSE 8080

# See https://www.databasesandlife.com/jetty-redirect-keep-https/
USER root
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
USER jetty

# Some things (e.g. Subversion, FOP) need to create dot-directories in Jetty's home dir
USER root
RUN mkdir -p -m 0777 /home/jetty
USER jetty

# Bug fix: Multipart sent (only) from the browser, fields would get amalgamated included
# the "boundary"
RUN echo "jetty.httpConfig.multiPartFormDataCompliance=LEGACY" >> /var/lib/jetty/start.d/server.ini

# Jetty supplies Mail but not the activation dependency, if you use javamail it uses Jetty's javamail
# which then fails on Java >= 9 (even if we supply the activation dependency in Maven, as Jetty's javamail
# uses Jetty's classloader rather than ours)
USER root
RUN rm -r /usr/local/jetty/lib/mail
USER jetty
