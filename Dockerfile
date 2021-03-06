FROM tomcat:8-jre8

MAINTAINER Rick Carter <rkcarter@umich.edu>

# I'm starting from a different base than he was, but trying this:
# Modified from: https://github.com/jorgemoralespou/s2i-java/blob/master/Dockerfile
# Install build tools on top of base image
# Java jdk 8, Maven 3.3
RUN apt-get update \
  && apt-get install -y openjdk-8-jdk git tar unzip bc lsof
    RUN mkdir -p /opt/openshift && \
    mkdir -p /opt/app-root/source && chmod -R a+rwX /opt/app-root/source && \
    mkdir -p /opt/s2i/destination && chmod -R a+rwX /opt/s2i/destination && \
    mkdir -p /opt/app-root/src && chmod -R a+rwX /opt/app-root/src

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

ENV MAVEN_VERSION 3.3.9
RUN (curl -0 http://www.eu.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | \
    tar -zx -C /usr/local) && \
    mv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven && \
    ln -sf /usr/local/maven/bin/mvn /usr/local/bin/mvn && \
    mkdir -p $HOME/.m2 && chmod -R a+rwX $HOME/.m2

WORKDIR /tmp

# Copy code to local directory for building
COPY . /tmp

# RUN mvn install # only need one
RUN mvn clean install sakai:deploy -Dmaven.tomcat.home=/usr/local/tomcat
# Build CCM and place the resulting war in the tomcat dir.
#RUN mvn clean install \
#	&& mv ./target/ctools-project-migration-0.1.0.war /usr/local/tomcat/webapps/ROOT.war
# Remove unnecessary build dependencies.
RUN unlink /usr/local/bin/mvn \
  && rm -rf /usr/local/maven
RUN apt-get remove -y openjdk-8-jdk git tar unzip bc lsof \
 && apt-get autoremove -y

WORKDIR /usr/local/tomcat/webapps

RUN rm -rf ROOT

# EXPOSE 8080
EXPOSE 8009

RUN mkdir /usr/local/tomcat/home/

# Launch Tomcat
# CMD cp /tmp/cpm-props/*.properties /usr/local/tomcat/home/; cp /tmp/cpm-props/server.xml /usr/local/tomcat/conf/; cp /tmp/jdbc-driver/* /usr/local/tomcat/lib/; catalina.sh run
# TODO: Figure out proper tomcat configuration at: https://confluence.sakaiproject.org/display/BOOT/Install+Tomcat+8
CMD catalina.sh run
