pipeline {
    agent any

    environment {
        // Définir les variables globales
        DOCKER_IMAGE = 'harbor.mon-domaine.local/marketplace/app'
        SCANNER_HOME = tool 'SonarQubeScanner'
        SONAR_HOST_URL = 'http://192.168.1.100:9000'
        // Remplacer par l'ID réel des credentials dans Jenkins
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
        HARBOR_CREDENTIALS_ID = 'harbor-credentials'
        SSH_PROD_CREDENTIALS_ID = 'ssh-prod-key'
    }

    stages {
        stage('Checkout & Build') {
            steps {
                echo "Récupération du code et compilation (Packaging) avec Maven..."
                dir('marketplace') {
                    // Exécution sans tests pour le build initial
                    bat 'mvnw.cmd clean package -DskipTests'
                }
            }
        }

        stage('Test & Analyse Statique (SonarQube)') {
            steps {
                echo "Lancement des tests avec analyse SonarQube..."
                dir('marketplace') {
                    withCredentials([string(credentialsId: "${SONAR_CREDENTIALS_ID}", variable: 'SONAR_TOKEN')]) {
                        bat """
                        mvnw.cmd verify sonar:sonar \
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
                    bat "docker build -t ${DOCKER_IMAGE}:${env.BUILD_ID} -t ${DOCKER_IMAGE}:latest ."
                }
            }
        }

        stage('Push vers Harbor & Scan Trivy') {
            steps {
                echo "Envoi de l'image vers la registry Harbor pour le stockage et l'analyse de sécurité Trivy..."
                withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                    bat "docker login harbor.mon-domaine.local -u %HARBOR_USER% -p %HARBOR_PASS%"
                    bat "docker push ${DOCKER_IMAGE}:${env.BUILD_ID}"
                    bat "docker push ${DOCKER_IMAGE}:latest"
                }
            }
        }

        stage('Déploiement Sécurisé avec Ansible') {
            steps {
                echo "Lancement du playbook Ansible pour déployer l'application sur le serveur de production..."
                withCredentials([usernamePassword(credentialsId: "${HARBOR_CREDENTIALS_ID}", passwordVariable: 'HARBOR_PASS', usernameVariable: 'HARBOR_USER')]) {
                    dir('ansible') {
                        // Assurez-vous que le plugin Ansible est installé sur Jenkins
                        ansiblePlaybook(
                            playbook: 'deploy-app.yml',
                            inventory: 'inventory.ini',
                            credentialsId: "${SSH_PROD_CREDENTIALS_ID}",
                            extraVars: [
                                harbor_user: "${HARBOR_USER}",
                                harbor_password: "${HARBOR_PASS}",
                                db_user: 'admin',                // Idéalement à injecter via Vault
                                db_password: 'Secr3tPasswordDB!',// Idéalement à injecter via Vault
                                mail_user: 'contact@marketplace.com',
                                mail_password: 'Secr3tPasswordMail!'
                            ]
                        )
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo "Nettoyage du workspace..."
            cleanWs()
            // Se déconnecter de Docker pour éviter de laisser les identifiants en cache
            bat "docker logout harbor.mon-domaine.local || exit 0"
        }
        success {
            echo "Le déploiement sécurisé (DevSecOps) a été exécuté avec succès!"
        }
        failure {
            echo "Une erreur a interrompu la pipeline. Vérifiez les logs (possible échec du Quality Gate, des tests ou de connexion Harbor/SSH)."
        }
    }
}
