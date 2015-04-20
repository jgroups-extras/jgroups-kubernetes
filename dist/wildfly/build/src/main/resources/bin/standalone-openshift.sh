#!/bin/sh
#shortcut to boot JBoss WildFly with openshift.DNS_PING configuration
set -ue

# Set HA args
JBOSS_HA_ARGS=""
if [ -n "${EAP_NODE_NAME+_}" ]; then
    JBOSS_NODE_NAME="${EAP_NODE_NAME}"
elif [ -n "${container_uuid+_}" ]; then
    JBOSS_NODE_NAME="${container_uuid}"
elif [ -n "${HOSTNAME+_}" ]; then
    JBOSS_NODE_NAME="${HOSTNAME}"
fi
if [ -n "${JBOSS_NODE_NAME+_}" ]; then
    JBOSS_HA_ARGS="-b ${JBOSS_NODE_NAME} -Djboss.node.name=${JBOSS_NODE_NAME}"
fi

exec $JBOSS_HOME/bin/standalone.sh -c standalone-openshift.xml $JBOSS_HA_ARGS
