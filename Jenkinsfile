#!/usr/bin/env groovy

pipeline {
  agent any
    stages {
      stage("Initialization") {
        steps {
          script {
            sh './build.sh init'
            def version = sh(returnStdout: true, script: 'docker run --rm -u `id -u`:`id -g` --env MAVEN_CONFIG=/var/maven/.m2 -w /usr/src/maven -v ./:/usr/src/maven -v ~/.m2:/var/maven/.m2  opendigitaleducation/mvn-java8-node20:latest mvn -Duser.home=/var/maven help:evaluate -Dexpression=project.version -DforceStdout -q')
            buildName "${env.GIT_BRANCH.replace("origin/", "")}@${version}"
          }
        }
      }
      stage('Build') {
        steps {
          checkout scm
          sh './build.sh init clean install publish'
        }
      }
      stage('Build image') {
        steps {
          sh './edifice image --archs=linux/amd64 --force --rebuild=false'
        }
      }
    }
  post {
    cleanup {
      sh 'docker-compose down'
    }
  }
}

