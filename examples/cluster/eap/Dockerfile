# JBoss EAP with openshift.DNS_PING impl and examples.
# Name: 10.245.2.2:5000/dward/eap-openshift-examples:latest

FROM 10.245.2.2:5000/dward/eap-openshift:latest

USER root

ADD standalone-openshift.xml /opt/jboss/eap/standalone/configuration/standalone-openshift.xml
RUN chown jboss:jboss /opt/jboss/eap/standalone/configuration/standalone-openshift.xml

ADD basic-app-cache.war /opt/jboss/eap/standalone/deployments/basic-app-cache.war
RUN chown jboss:jboss /opt/jboss/eap/standalone/deployments/basic-app-cache.war

ADD basic-web-session.war /opt/jboss/eap/standalone/deployments/basic-web-session.war
RUN chown jboss:jboss /opt/jboss/eap/standalone/deployments/basic-web-session.war

ADD hello-servlet.war /opt/jboss/eap/standalone/deployments/hello-servlet.war
RUN chown jboss:jboss /opt/jboss/eap/standalone/deployments/hello-servlet.war

USER jboss
