FROM tomcat:9-jdk21

ENV CATALINA_OPTS="-Djava.security.egd=file:/dev/./urandom"

RUN rm -rf /usr/local/tomcat/webapps/*

COPY target/dalvalidate.war /usr/local/tomcat/webapps/
