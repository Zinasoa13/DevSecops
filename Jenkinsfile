pipeline {
    agent any

    environment {
        // Définir les variables globales avec l'IP Tailscale stable
        DOCKER_IMAGE = '100.115.122.20/marketplace/app'
        SONAR_HOST_URL = 'http://100.115.122.20:9000'
        // IDs des credentials configurés dans Jenkins
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
        HARBOR_CREDENTIALS_ID = 'harbor-credentials'
        SSH_PROD_CREDENTIALS_ID = 'ssh-ubuntu-root'
    }

    stages {
        stage('Checkout & Build') {
            steps {
                echo "Récupération du code et compilation (Packaging) avec Maven..."
                dir('marketplace') {
                    // S'assurer que le wrapper Maven est exécutable sur Linux
                    sh 'chmod +x mvnw'
                    sh './mvnw clean package -DskipTests'
                }
            }
        }

        stage('Test & Analyse Statique (SonarQube)') {
            steps {
                echo "Lancement des tests avec analyse SonarQube..."
                dir('marketplace') {
                    withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                        sh """
                        ./mvnw verify sonar:sonar \
                          -Dsonar.projectKey=marketplace \
                          -Dsonar.host.url=${SONAR_HOST_URL} \
                          -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo "Attente de la validation du Quality Gate SonarQube..."
                timeout(time: 5, unit: 'MINUTES') {
                    // Attend que SonarQube donne le feu vert (nécessite le webhook Sonar -> Jenkins)
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Construction Image Docker') {
            steps {
                echo "Construction de l'Image Docker via le Dockerfile multi-stage..."
                dir('marketplace') {
                    sh "docker build -t ${DOCKER_IMAGE}:${env.BUILD_ID} -t ${DOCKER_IMAGE}:latest ."
                }
            }
        }

        stage('Push vers Harbor & Scan Trivy') {
            steps {
                echo "Envoi de l'image vers la registry Harbor (Tailscale) pour le stockage et scan Trivy..."
                withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                    sh "docker login 100.115.122.20 -u \${HARBOR_USER} -p \${HARBOR_PASS}"
                    sh "docker push ${DOCKER_IMAGE}:${env.BUILD_ID}"
                    sh "docker push ${DOCKER_IMAGE}:latest"
                }
            }
        }

    stage('Déploiement Sécurisé avec Ansible (via SSH)') {
        steps {
            echo "Pilotage à distance d'Ansible sur l'hôte Ubuntu WSL via SSH..."
            script {
                def remote = [:]
                remote.name = 'wsl-ubuntu'
                remote.host = '100.115.122.20'
                remote.allowAnyHosts = true
                
                withCredentials([usernamePassword(credentialsId: "${SSH_PROD_CREDENTIALS_ID}", passwordVariable: 'SSH_PASS', usernameVariable: 'SSH_USER')]) {
                    remote.user = SSH_USER
                    remote.password = SSH_PASS
                    
                    withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                        // Exécution du playbook Ansible resté sur l'hôte WSL
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
    
    post {
        always {
            echo "Nettoyage du workspace..."
            cleanWs()
            // Déconnexion Docker (IP Tailscale)
            sh "docker logout 100.115.122.20 || exit 0"
        }
        success {
            echo "Le déploiement sécurisé (DevSecOps) a été exécuté avec succès!"
        }
        failure {
            echo "Une erreur a interrompu la pipeline. Vérifiez les logs (possible échec du Quality Gate, des tests ou de connexion Harbor/SSH)."
        }
    }
}
