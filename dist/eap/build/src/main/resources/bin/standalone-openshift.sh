#!/bin/sh
#shortcut to boot JBoss EAP with openshift.KUBE_PING configuration
#if you need to set other boot options, please use standalone.sh

DIRNAME=`dirname "$0"`
#REALPATH=`cd "$DIRNAME/../bin"; pwd`

#DOCKER_IP=$(ip addr show eth0 | grep -E '^\s*inet' | grep -m1 global | awk '{ print $2 }' | sed 's|/.*||')
#${DIRNAME}/standalone.sh -b $DOCKER_IP -c standalone-openshift.xml

DOCKER_UNAME=`uname -n`
${DIRNAME}/standalone.sh -b $DOCKER_UNAME -c standalone-openshift.xml
