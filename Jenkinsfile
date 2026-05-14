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
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

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

                            echo "Préparation propre du répertoire..."
                            sshCommand remote: remote, command: """
                                echo '${SSH_PASS}' | sudo -S rm -rf /opt/devsecops/marketplace /opt/devsecops/ansible /opt/devsecops/serveurs
                                echo '${SSH_PASS}' | sudo -S mkdir -p /opt/devsecops
                                echo '${SSH_PASS}' | sudo -S chown -R ${SSH_USER}:${SSH_USER} /opt/devsecops
                            """

                            echo "Transfert des fichiers (Ansible, Marketplace, Serveurs)..."
                            sshPut remote: remote, from: 'ansible', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'marketplace', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'serveurs', into: '/opt/devsecops'

                            echo "Exécution du playbook d'infrastructure..."
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
                    remote.timeout = 300000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                                sshCommand remote: remote, command: """
                                    echo "Lancement de l'analyse Sonar (Audit Mode)..." && \
                                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && \
                                    cd /opt/devsecops/marketplace && \
                                    ./mvnw sonar:sonar -DskipTests \
                                      -Dsonar.ws.timeout=300 \
                                      -Dsonar.qualitygate.wait=false \
                                      -Dsonar.projectKey=marketplace \
                                      -Dsonar.host.url=${SONAR_HOST_URL} \
                                      -Dsonar.login=${SONAR_TOKEN} || \
                                    echo "ATTENTION : Le scan Sonar a détecté des problèmes, mais le pipeline continue..."
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Audit SonarQube (Logs)') {
            steps {
                script {
                    def remote = [:]
                    remote.name = 'wsl-ubuntu'
                    remote.host = '100.115.122.20'
                    remote.allowAnyHosts = true
                    remote.timeout = 300000

                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        
                        withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                            echo "--- RÉSUMÉ DES PROBLÈMES SONARQUBE ---"
                            sshCommand remote: remote, command: """
                                curl -u ${SONAR_TOKEN}: "${SONAR_HOST_URL}/api/qualitygates/project_status?projectKey=marketplace" | python3 -m json.tool
                            """
                        }
                    }
                }
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
                    remote.timeout = 300000

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
                    remote.timeout = 600000

                    retry(3) {
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            sshCommand remote: remote, command: """
                                trivy image --db-repository ghcr.io/aquasecurity/trivy-db:2 --timeout 10m --severity HIGH,CRITICAL --exit-code 0 ${DOCKER_IMAGE}:${env.BUILD_ID}
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
                    remote.timeout = 300000

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
                    remote.timeout = 300000

                    retry(3) {
                        withCredentials([
                            usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER'),
                            string(credentialsId: 'COSIGN_PASSWORD', variable: 'COSIGN_PWD'),
                            file(credentialsId: 'COSIGN_KEY', variable: 'KEY_FILE'),
                            usernamePassword(credentialsId: "${DOCKERHUB_CREDENTIALS_ID}", passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')
                        ]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS

                            sshPut remote: remote, from: "${KEY_FILE}", into: "/tmp/cosign.key"

                            sshCommand remote: remote, command: """
                                # Authentification pour Cosign via variables d'environnement
                                export COSIGN_PASSWORD='${COSIGN_PWD}'
                                export COSIGN_EXPERIMENTAL=true
                                
                                # On utilise le token Docker directement pour l'authentification de Cosign
                                export DOCKER_CONFIG=/tmp/.docker
                                mkdir -p /tmp/.docker
                                echo '{"auths": {"https://index.docker.io/v1/": {"auth": "'\$(echo -n ${DOCKER_USER}:${DOCKER_PASS} | base64)'"}}}' > /tmp/.docker/config.json
                                
                                # Signature
                                cosign sign --key /tmp/cosign.key --yes ${DOCKER_IMAGE}:${env.BUILD_ID}
                                
                                # Nettoyage
                                rm /tmp/cosign.key
                                rm -rf /tmp/.docker
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
            cleanWs()
        }
    }
}