FROM tomcat:8-jre8

MAINTAINER Rick Carter <rkcarter@umich.edu>

# I'm starting from a different base than he was, but trying this:
# From: https://github.com/jorgemoralespou/s2i-java/blob/master/Dockerfile
# Install build tools on top of base image
# Java jdk 8, Maven 3.3, Gradle 2.6
RUN INSTALL_PKGS="tar unzip bc which lsof java-1.8.0-openjdk java-1.8.0-openjdk-devel" && \
    yum install -y --enablerepo=centosplus $INSTALL_PKGS && \
    rpm -V $INSTALL_PKGS && \
    yum clean all -y && \
    mkdir -p /opt/openshift && \
    mkdir -p /opt/app-root/source && chmod -R a+rwX /opt/app-root/source && \
    mkdir -p /opt/s2i/destination && chmod -R a+rwX /opt/s2i/destination && \
    mkdir -p /opt/app-root/src && chmod -R a+rwX /opt/app-root/src

ENV MAVEN_VERSION 3.3.9
RUN (curl -0 http://www.eu.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | \
    tar -zx -C /usr/local) && \
    mv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven && \
    ln -sf /usr/local/maven/bin/mvn /usr/local/bin/mvn && \
    mkdir -p $HOME/.m2 && chmod -R a+rwX $HOME/.m2


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
