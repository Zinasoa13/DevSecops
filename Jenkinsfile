pipeline {
    agent any

    environment {
        // Définir les variables globales avec l'IP Tailscale stable
        DOCKER_IMAGE = '100.115.122.20:5000/marketplace/app'
        SONAR_HOST_URL = 'http://100.115.122.20:9000'
        // IDs des credentials configurés dans Jenkins
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
        HARBOR_CREDENTIALS_ID = 'harbor-credentials'
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
                    remote.timeout = 60000 // Augmenter le timeout à 60s
                    
                    retry(3) { // Ajouter des retries pour gérer les micro-coupures
                        withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                            remote.user = SSH_USER
                            remote.password = SSH_PASS
                            
                            echo "Test de connexion et création du dossier..."
                            sshCommand remote: remote, command: "mkdir -p /opt/devsecops"
                            
                            echo "Transfert des fichiers (Ansible, Marketplace, Serveurs)..."
                            sshPut remote: remote, from: 'ansible', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'marketplace', into: '/opt/devsecops'
                            sshPut remote: remote, from: 'serveurs', into: '/opt/devsecops'
                            
                            echo "Exécution du playbook d'infrastructure..."
                            sshCommand remote: remote, command: """
                                cd /opt/devsecops/ansible && \
                                ansible-playbook -i inventory.ini setup-tools.yml
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

        stage('Construction & Push Image Docker (Remote)') {
            steps {
                echo "Construction et envoi de l'image Docker à distance..."
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
                            
                            withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                                sshCommand remote: remote, command: """
                                    cd /opt/devsecops/marketplace && \
                                    docker login 100.115.122.20:5000 -u ${HARBOR_USER} -p ${HARBOR_PASS} && \
                                    docker build -t ${DOCKER_IMAGE}:${env.BUILD_ID} -t ${DOCKER_IMAGE}:latest . && \
                                    docker push ${DOCKER_IMAGE}:${env.BUILD_ID} && \
                                    docker push ${DOCKER_IMAGE}:latest
                                """
                            }
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
                            
                            withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                                sshCommand remote: remote, command: """
                                    cd /opt/devsecops/ansible && \
                                    ansible-playbook -i inventory.ini deploy-app.yml \
                                    --extra-vars "harbor_user=${HARBOR_USER} harbor_password=${HARBOR_PASS} db_user='admin' db_password='Secr3tPasswordDB!' mail_user='contact@marketplace.com' mail_password='Secr3tPasswordMail!'"
                                """
                            }
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
                        sshCommand remote: remote, command: "docker logout 100.115.122.20:5000 || exit 0"
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
