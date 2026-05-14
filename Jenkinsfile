pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'sushibaka/marketplace-app'
        SONAR_HOST_URL = 'http://100.115.122.20:9000'
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        SSH_PROD_CREDENTIALS_ID = 'ssh-ubuntu-root'
        
        // Injected variables: SSH_USR, SSH_PSW
        SSH = credentials("${SSH_PROD_CREDENTIALS_ID}")
        // Injected variables: DOCKER_USR, DOCKER_PSW
        DOCKER = credentials("${DOCKERHUB_CREDENTIALS_ID}")
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
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        echo '${env.SSH_PSW}' | sudo -S rm -rf /opt/devsecops/marketplace /opt/devsecops/ansible /opt/devsecops/serveurs
                        echo '${env.SSH_PSW}' | sudo -S mkdir -p /opt/devsecops
                        echo '${env.SSH_PSW}' | sudo -S chown -R ${env.SSH_USR}:${env.SSH_USR} /opt/devsecops
                    """
                    sshPut remote: remote, from: 'ansible', into: '/opt/devsecops'
                    sshPut remote: remote, from: 'marketplace', into: '/opt/devsecops'
                    sshPut remote: remote, from: 'serveurs', into: '/opt/devsecops'
                    sshCommand remote: remote, command: "cd /opt/devsecops/ansible && ansible-playbook -i inventory.ini setup-tools.yml --extra-vars \"ansible_become_pass='${env.SSH_PSW}'\""
                }
            }
        }

        stage('Build Maven (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 60000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                        cd /opt/devsecops/marketplace && chmod +x mvnw && ./mvnw clean package -DskipTests
                    """
                }
            }
        }

        stage('Test & Analyse Statique (Audit Mode)') {
            environment {
                SONAR_TOKEN = credentials("${SONAR_CREDENTIALS_ID}")
            }
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 300000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                        cd /opt/devsecops/marketplace && ./mvnw sonar:sonar -Dsonar.qualitygate.wait=true -Dsonar.projectKey=marketplace -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${env.SONAR_TOKEN}
                    """
                }
            }
        }

        stage('Construction Image Docker (Isolée)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        export DOCKER_CONFIG=/tmp/docker_config
                        mkdir -p \$DOCKER_CONFIG
                        echo '${env.DOCKER_PSW}' | docker --config \$DOCKER_CONFIG login -u '${env.DOCKER_USR}' --password-stdin
                        cd /opt/devsecops/marketplace && docker --config \$DOCKER_CONFIG build -t ${env.DOCKER_IMAGE}:${env.BUILD_ID} -t ${env.DOCKER_IMAGE}:latest .
                        docker --config \$DOCKER_CONFIG push ${env.DOCKER_IMAGE}:${env.BUILD_ID}
                        docker --config \$DOCKER_CONFIG push ${env.DOCKER_IMAGE}:latest
                        rm -rf \$DOCKER_CONFIG
                    """
                }
            }
        }

        stage('Sécurité : Scan Trivy (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 1200000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        export DOCKER_CONFIG=/tmp/docker_trivy_config
                        mkdir -p \$DOCKER_CONFIG
                        echo '${env.DOCKER_PSW}' | docker --config \$DOCKER_CONFIG login -u '${env.DOCKER_USR}' --password-stdin
                        
                        trivy --cache-dir /tmp/trivy-cache image --download-db-only --timeout 20m
                        trivy --cache-dir /tmp/trivy-cache image --timeout 20m --severity HIGH,CRITICAL --exit-code 0 ${env.DOCKER_IMAGE}:${env.BUILD_ID}
                        
                        rm -rf \$DOCKER_CONFIG
                    """
                }
            }
        }

        stage('Sécurité : Signature Cosign (Remote)') {
            environment {
                COSIGN_PASSPHRASE = credentials('COSIGN_PASSWORD')
                COSIGN_KEY_FILE = credentials('COSIGN_KEY')
            }
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshPut remote: remote, from: "${env.COSIGN_KEY_FILE}", into: "/tmp/cosign.key"
                    sshCommand remote: remote, command: """
                        export DOCKER_CONFIG=/tmp/docker_config
                        mkdir -p \$DOCKER_CONFIG
                        echo '${env.DOCKER_PSW}' | docker --config \$DOCKER_CONFIG login -u '${env.DOCKER_USR}' --password-stdin
                        
                        export COSIGN_PASSWORD='${env.COSIGN_PASSPHRASE}'
                        cosign sign --key /tmp/cosign.key --yes ${env.DOCKER_IMAGE}:${env.BUILD_ID}
                        
                        rm /tmp/cosign.key && rm -rf \$DOCKER_CONFIG
                    """
                }
            }
        }

        stage('Déploiement avec Ansible (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 600000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
                    sshCommand remote: remote, command: """
                        cd /opt/devsecops/ansible && \
                        ansible-playbook -i inventory.ini deploy-app.yml \
                        -e "docker_user=${env.DOCKER_USR}" -e "docker_pass=${env.DOCKER_PSW}"
                    """
                }
            }
        }

        stage('Vérification du Déploiement (Remote)') {
            steps {
                script {
                    def remote = [name: 'wsl-ubuntu', host: '100.115.122.20', allowAnyHosts: true, timeout: 60000]
                    remote.user = env.SSH_USR
                    remote.password = env.SSH_PSW
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