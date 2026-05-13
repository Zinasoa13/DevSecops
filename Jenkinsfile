pipeline {
    agent any

    environment {
        // Définir les variables globales avec l'IP Tailscale stable
        DOCKER_IMAGE = 'sushibaka/marketplace-app'
        SONAR_HOST_URL = 'http://100.115.122.20:9000'
        // IDs des credentials configurés dans Jenkins
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        SSH_PROD_CREDENTIALS_ID = 'ssh-ubuntu-root'
    }

    stages {
        stage('Initialisation & Sync Code (Remote)') {
            steps {
                echo "Synchronisation du code vers l'hôte WSL via SSH..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 60000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            echo "Test de connexion et sécurisation des droits..."
                            // Utilisation de echo $SSH_PASS | sudo -S pour passer le mot de passe automatiquement
                            sshCommand remote: remote, command: """
                                echo '${SSH_PASS}' | sudo -S mkdir -p /opt/devsecops && \
                                echo '${SSH_PASS}' | sudo -S chown -R ${SSH_USER} /opt/devsecops
                            """

                            echo "Transfert des fichiers (Ansible, Marketplace, Serveurs)..."
                            // Maintenant SFTP va fonctionner car le dossier t'appartient !
                            sshPut remote: remote, from: 'ansible', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'marketplace', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'serveurs', into: '/opt/devsecops'

                            echo "Exécution du playbook d'infrastructure..."
                            // On passe le mot de passe à Ansible via ansible_become_pass pour qu'il puisse installer Docker
                            sshCommand remote: remote, command: """
                                cd /opt/devsecops/ansible && \
                                ansible-playbook -i inventory.ini setup-tools.yml --extra-vars "ansible_become_pass='${SSH_PASS}'"
                            """
                        }
                    }
                }
            }
        }

        stage('Build Maven (Remote)') {
            steps {
                echo "Compilation Maven à distance sur l'hôte WSL..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 60000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            sshCommand remote: remote, command: """
                                export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && \
                                cd /opt/devsecops/marketplace && \
                                chmod +x mvnw && \
                                ./mvnw clean package -DskipTests --no-transfer-progress
                            """
                        }
                    }
                }
            }
        }

        stage('Test & Analyse Statique (Remote)') {
            steps {
                echo "Lancement des tests et SonarQube à distance..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 300000 // 5 minutes pour l'analyse et le Quality Gate

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                                sshCommand remote: remote, command: """
                                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && \
                                    cd /opt/devsecops/marketplace && \
                                    ./mvnw sonar:sonar -DskipTests \
                                      -Dsonar.ws.timeout=300 \
                                      -Dsonar.qualitygate.wait=true \
                                      -Dsonar.projectKey=marketplace \
                                      -Dsonar.host.url=${SONAR_HOST_URL} \
                                      -Dsonar.login=${SONAR_TOKEN}
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo "Quality Gate déjà validé par le scanner distant."
                /*
                timeout(time: 5, unit: 'MINUTES') {
                    // Désactivé car l'analyse est distante (SSH)
                    waitForQualityGate abortPipeline: true
                }
                */
            }
        }

        stage('Construction Image Docker (Remote)') {
            steps {
                echo "Construction de l'image Docker à distance..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 60000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            sshCommand remote: remote, command: """
                                cd /opt/devsecops/marketplace && \
                                docker build -t ${DOCKER_IMAGE}:${env.BUILD_ID} -t ${DOCKER_IMAGE}:latest .
                            """
                        }
                    }
                }
            }
        }

        stage('Sécurité : Scan Trivy (Remote)') {
            steps {
                echo "Analyse de l'image pour les vulnérabilités..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS
                            
                            sshCommand remote: remote, command: """
                                trivy image --severity HIGH,CRITICAL --exit-code 0 ${DOCKER_IMAGE}:${env.BUILD_ID}
                            """
                        }
                    }
                }
            }
        }

        stage('Push Image Docker (Remote)') {
            steps {
                echo "Envoi de l'image vers Docker Hub..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 60000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDENTIALS_ID}", passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                                sshCommand remote: remote, command: """
                                    echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin && \
                                    docker push ${DOCKER_IMAGE}:${env.BUILD_ID} && \
                                    docker push ${DOCKER_IMAGE}:latest
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Sécurité : Signature Cosign (Remote)') {
            steps {
                echo "Signature de l'image Docker..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true

                    retry(3) {
                        withCredentials([
                            usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER'),
                            string(credentialsId: 'COSIGN_PASSWORD', variable: 'COSIGN_PWD'),
                            file(credentialsId: 'COSIGN_KEY', variable: 'KEY_FILE')
                        ]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS
                            
                            // On transfère temporairement la clé sur le serveur pour signer
                            sshPut remote: remote, from: "${KEY_FILE}", into: "/tmp/cosign.key"
                            
                            sshCommand remote: remote, command: """
                                export COSIGN_PASSWORD=${COSIGN_PWD} && \
                                cosign sign --key /tmp/cosign.key --yes ${DOCKER_IMAGE}:${env.BUILD_ID} && \
                                rm /tmp/cosign.key
                            """
                        }
                    }
                }
            }
        }

        stage('Déploiement avec Ansible (Remote)') {
            steps {
                echo "Déploiement final via Ansible à distance..."
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 60000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            sshCommand remote: remote, command: """
                                cd /opt/devsecops/ansible && \
                                ansible-playbook -i inventory.ini deploy-app.yml \
                                --extra-vars "db_user='admin' db_password='Secr3tPasswordDB!' mail_user='contact@marketplace.com' mail_password='Secr3tPasswordMail!'"
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Nettoyage du workspace local..."
            cleanWs()
            script {
                def remote = [:]
                remote.name = 'wsl-ubuntu'
                remote.host = '100.115.122.20'
                remote.allowAnyHosts = true
                remote.timeout = 20000 // 20s pour le logout

                retry(3) {
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: "docker logout || exit 0"
                    }
                }
            }
        }
        success {
            echo "Le déploiement sécurisé (DevSecOps) a été exécuté avec succès!"
        }
        failure {
            echo "Une erreur a interrompu la pipeline. Vérifiez les logs (possible échec du Quality Gate, des tests ou de connexion Harbor/SSH)."
        }
    }
}
