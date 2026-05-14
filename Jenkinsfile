pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'sushibaka/marketplace-app'
        SONAR_HOST_URL = 'http://100.115.122.20:9000'
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
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 60000]
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            echo '${SSH_PASS}' | sudo -S rm -rf /opt/devsecops/marketplace /opt/devsecops/ansible /opt/devsecops/serveurs
                            echo '${SSH_PASS}' | sudo -S mkdir -p /opt/devsecops
                            echo '${SSH_PASS}' | sudo -S chown -R ${SSH_USER}:${SSH_USER} /opt/devsecops
                        """
                        sshPut remote: remote, from: 'ansible', into: '/opt/devsecops'
                        sshPut remote: remote, from: 'marketplace', into: '/opt/devsecops'
                        sshPut remote: remote, from: 'serveurs', into: '/opt/devsecops'
                        sshCommand remote: remote, command: "cd /opt/devsecops/ansible && ansible-playbook -i inventory.ini setup-tools.yml --extra-vars \"ansible_become_pass='${SSH_PASS}'\""
                    }
                }
            }
        }

        stage('Build Maven (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 60000]
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                            cd /opt/devsecops/marketplace && chmod +x mvnw && ./mvnw clean package -DskipTests
                        """
                    }
                }
            }
        }

        stage('Test & Analyse Statique (Audit Mode)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 300000]
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                            sshCommand remote: remote, command: """
                                export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                                cd /opt/devsecops/marketplace && ./mvnw sonar:sonar -Dsonar.qualitygate.wait=false -Dsonar.projectKey=marketplace -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                    }
                }
            }
        }

        stage('Construction Image Docker (Isolée)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
                    withCredentials([
                        usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER'),
                        usernamePassword(credentialsId: "${DOCKERHUB_CREDENTIALS_ID}", passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')
                    ]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            export DOCKER_CONFIG=/tmp/docker_config
                            mkdir -p \$DOCKER_CONFIG
                            echo '${DOCKER_PASS}' | docker --config \$DOCKER_CONFIG login -u '${DOCKER_USER}' --password-stdin
                            cd /opt/devsecops/marketplace && docker --config \$DOCKER_CONFIG build -t ${DOCKER_IMAGE}:${env.BUILD_ID} .
                            rm -rf \$DOCKER_CONFIG
                        """
                    }
                }
            }
        }

        stage('Sécurité : Scan Trivy (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 1200000]
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            trivy --cache-dir /tmp/trivy-cache image --download-db-only --timeout 20m
                            trivy --cache-dir /tmp/trivy-cache image --timeout 20m --severity HIGH,CRITICAL --exit-code 0 ${DOCKER_IMAGE}:${env.BUILD_ID}
                        """
                    }
                }
            }
        }

        stage('Sécurité : Signature Cosign (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
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
                            export DOCKER_CONFIG=/tmp/docker_config
                            mkdir -p \$DOCKER_CONFIG
                            echo '${DOCKER_PASS}' | docker --config \$DOCKER_CONFIG login -u '${DOCKER_USER}' --password-stdin
                            
                            export COSIGN_PASSWORD='${COSIGN_PWD}'
                            cosign sign --key /tmp/cosign.key --yes --repository index.docker.io ${DOCKER_IMAGE}:${env.BUILD_ID}
                            
                            rm /tmp/cosign.key && rm -rf \$DOCKER_CONFIG
                        """
                    }
                }
            }
        }

        stage('Déploiement avec Ansible (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
                    withCredentials([
                        usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER'),
                        usernamePassword(credentialsId: 'db-credentials', passwordVariable: 'DB_PASS', usernameVariable: 'DB_USER'),
                        usernamePassword(credentialsId: 'mail-credentials', passwordVariable: 'MAIL_PASS', usernameVariable: 'MAIL_USER')
                    ]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            cd /opt/devsecops/ansible && \
                            ansible-playbook -i inventory.ini deploy-app.yml \
                              --extra-vars "db_user=${DB_USER} db_password=${DB_PASS} mail_user=${MAIL_USER} mail_password=${MAIL_PASS}"
                        """
                    }
                }
            }
        }

        stage('Vérification du Déploiement (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 60000]
                    withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                        remote.user = SSH_USER
                        remote.password = SSH_PASS
                        sshCommand remote: remote, command: """
                            echo "--- État des conteneurs ---"
                            docker ps -a --filter name=marketplace
                            echo "--- Logs de l'application (Dernières 50 lignes) ---"
                            docker logs --tail 50 marketplace_app || true
                            echo "--- Logs de la base de données (Dernières 50 lignes) ---"
                            docker logs --tail 50 marketplace_db || true
                        """
                    }
                }
            }
        }
    }
}