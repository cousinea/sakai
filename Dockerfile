FROM tomcat:7-jre8

MAINTAINER Rick Carter <rkcarter@umich.edu>

# Maven is too old, 3.0.5, so need a later one
RUN apt-get purge maven maven2 maven3
RUN add-apt-repository ppa:andrei-pozolotin/maven3

RUN apt-get update \
 && apt-get install -y maven openjdk-8-jdk git

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

WORKDIR /tmp

# Copy CCM code to local directory for building
COPY . /tmp

RUN mvn install
RUN mvn clean install sakai:deploy -Dmaven.tomcat.home=/usr/local/tomcat/webapps/ROOT.war
# Build CCM and place the resulting war in the tomcat dir.
#RUN mvn clean install \
#	&& mv ./target/ctools-project-migration-0.1.0.war /usr/local/tomcat/webapps/ROOT.war
# Remove unnecessary build dependencies.
RUN apt-get remove -y maven openjdk-8-jdk git \
 && apt-get autoremove -y

WORKDIR /usr/local/tomcat/webapps

RUN rm -rf ROOT

# EXPOSE 8080
EXPOSE 8009

RUN mkdir /usr/local/tomcat/home/

# Launch Tomcat
CMD cp /tmp/cpm-props/*.properties /usr/local/tomcat/home/; cp /tmp/cpm-props/server.xml /usr/local/tomcat/conf/; cp /tmp/jdbc-driver/* /usr/local/tomcat/lib/; catalina.sh run
