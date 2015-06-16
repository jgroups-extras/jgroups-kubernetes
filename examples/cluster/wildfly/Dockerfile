# JBoss WildFly with openshift.DNS_PING impl and examples.
# Name: 10.245.2.2:5000/dward/wildfly-openshift-examples:latest

FROM 10.245.2.2:5000/dward/wildfly-openshift:latest

USER root

ADD standalone-openshift.xml /opt/jboss/wildfly/standalone/configuration/standalone-openshift.xml
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/configuration/standalone-openshift.xml

ADD basic-app-cache.war /opt/jboss/wildfly/standalone/deployments/basic-app-cache.war
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/basic-app-cache.war

ADD basic-web-session.war /opt/jboss/wildfly/standalone/deployments/basic-web-session.war
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/basic-web-session.war

ADD hello-servlet.war /opt/jboss/wildfly/standalone/deployments/hello-servlet.war
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/hello-servlet.war

USER jboss
