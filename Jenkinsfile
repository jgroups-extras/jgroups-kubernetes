#!/usr/bin/env groovy

pipeline {
    agent any
    stages {
        stage('SCM Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                script {
                    env.JAVA_HOME = tool('JDK 11')
                    def mvnHome = tool 'Maven'
                    sh "${mvnHome}/bin/mvn clean install -Dmaven.test.failure.ignore=true"
                    junit '**/target/*-reports/*.xml'
                }
            }
        }

        
        stage('Deploy SNAPSHOT') {
            when {
                branch 'master'
            }
            steps {
                configFileProvider([configFile(fileId: 'maven-settings-with-deploy-snapshot', variable: 'MAVEN_SETTINGS')]) {
                    script {
                        env.JAVA_HOME = tool('JDK 11')
                        def mvnHome = tool 'Maven'
                        sh "${mvnHome}/bin/mvn deploy -s $MAVEN_SETTINGS -DskipTests"
                    }
                }
            }
        }
    }
}
