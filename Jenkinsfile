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
                sh './mvnw --batch-mode --no-transfer-progress clean install'
                junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml'
            }
        }
    }
}
