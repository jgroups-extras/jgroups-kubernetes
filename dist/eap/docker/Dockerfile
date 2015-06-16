# JBoss EAP with openshift.DNS_PING impl.
# Name: 10.245.2.2:5000/dward/eap-openshift:latest

# Use latest jboss/base-jdk:7 image as the base
FROM jboss/base-jdk:7

# Set the EAP_VERSION env variable
ENV EAP_VERSION 6.4.0.Beta

# Add the JBoss EAP OpenShift distribution to /opt, and make jboss the owner
# Make sure the distribution is available from a well-known place
RUN cd /opt/jboss
ADD jboss-eap-openshift.zip /opt/jboss/
RUN unzip $HOME/jboss-eap-openshift.zip -d /opt/jboss

# Set the JBOSS_HOME env variable
ENV JBOSS_HOME /opt/jboss/eap

# Expose the ports we're interested in
EXPOSE 8080 8888 9990

# Set the default command to run on boot
# This will boot JBoss EAP in the standalone mode and bind to all interface
CMD ["/opt/jboss/eap/bin/standalone-openshift.sh"]
