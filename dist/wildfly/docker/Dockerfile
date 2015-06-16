# JBoss WildFly with openshift.DNS_PING impl.
# Name: 10.245.2.2:5000/dward/wildfly-openshift:latest

# Use latest jboss/base-jdk:7 image as the base
FROM jboss/base-jdk:7

# Set the WILDFLY_VERSION env variable
ENV WILDFLY_VERSION 8.2.0.Final

# Add the WildFly OpenShift distribution to /opt, and make jboss the owner
# Make sure the distribution is available from a well-known place
RUN cd /opt/jboss
ADD wildfly-openshift.zip /opt/jboss/
RUN unzip $HOME/wildfly-openshift.zip -d /opt/jboss

# Set the JBOSS_HOME env variable
ENV JBOSS_HOME /opt/jboss/wildfly

# Expose the ports we're interested in
EXPOSE 8080 8888 9990

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["/opt/jboss/wildfly/bin/standalone-openshift.sh"]
