# Guide de Déploiement DevSecOps - Projet Marketplace

Ce document détaille les étapes pour initialiser l'infrastructure serveurs et lancer le pipeline CI/CD automatisé de l'application Marketplace.

## Prérequis

1. **Une machine "Outils" (ex: Ubuntu)** : Pour héberger SonarQube et Harbor.
2. **Une machine "Production" (ex: Ubuntu)** : Pour faire tourner l'application (Marketplace + MySQL).
3. **Un serveur Jenkins** : Qui fera tourner le `Jenkinsfile`. Il doit avoir `Ansible`, `Docker` (client) et `Maven` installés.
4. Les accès SSH (clés publiques/privées) configurés pour que Jenkins puisse gérer le serveur de Outils et le serveur de Prod via Ansible.

---

## Étape 1 : Personnaliser l'Inventaire Ansible

Ouvrez le fichier `ansible/inventory.ini` et indiquez les véritables adresses IP de vos serveurs de la manière suivante :

```ini
[outils]
server_outils ansible_host=VOTRE_IP_OUTILS ansible_user=ubuntu

[prod]
server_prod ansible_host=VOTRE_IP_PROD ansible_user=ubuntu
```

*(Remplacer `ubuntu` par votre nom d'utilisateur serveur si différent).*

---

## Étape 2 : Préparation de l'Infrastructure DevSecOps (SonarQube & Harbor)

On va utiliser Ansible pour installer Docker et mettre en route la "Forge" (SonarQube et Harbor) sur le serveur `outils`.

Depuis votre terminal sur la machine où l'outil Ansible est disponible, exécutez le playbook de configuration :

```bash
cd ansible
ansible-playbook -i inventory.ini setup-tools.yml
```

> ⚠️ Dès l'exécution terminée, vos services devront être en ligne :
> - **SonarQube** : `http://VOTRE_IP_OUTILS:9000` (identifiants par défaut: `admin` / `admin`)
> - **Harbor** : `http://harbor.mon-domaine.local` (ou `VOTRE_IP_OUTILS` au port `80`)

---

## Étape 3 : Configuration de SonarQube et Harbor (Configurations initiales Web)

### Dans SonarQube (http://VOTRE_IP_OUTILS:9000)
1. Connectez-vous (il vous sera demandé de modifier le mot de passe `admin`).
2. Allez dans **Administration** > **Security** > **Users**.
3. Dans la liste, à droite de votre compte administrateur, cliquez sur l'icône de jeton (Token) pour générer un **Token utilisateur** (donnez-lui le nom `jenkins-token`).
4. **Copiez et conservez ce Token PRÉCIEUSEMENT**. Il servira à Jenkins.

### Dans Harbor (http://VOTRE_IP_OUTILS:80)
1. Connectez-vous avec `admin` / `HarborAdmin12345` (tel que défini dans le fichier `harbor.yml`).
2. Créez un nouveau projet (nom : `marketplace`) via l'interface afin que les images Docker poussées par Jenkins sachent où être stockées.

---

## Étape 4 : Définition des Secrets dans Jenkins

Dans l'interface web de votre répertoire **Jenkins** :
Allez dans **Gérer Jenkins** > **Manage Credentials** > **System** > **Global credentials (unrestricted)** et ajoutez :

1. **Jeton SonarQube** : 
   - Kind : Secret text
   - Secret : `[Le Token généré dans Sonar à l'étape 3]`
   - ID : `sonarqube-token`

2. **Identifiants Harbor** :
   - Kind : Username with password
   - Username : `admin`
   - Password : `HarborAdmin12345`
   - ID : `harbor-credentials`

3. **Clé SSH pour la Production** :
   - Kind : SSH Username with private key
   - Username : `ubuntu` (ou le nom d'utilisateur prod)
   - Private Key : Entrez la clé privée utilisée pour vous connecter au serveur prod
   - ID : `ssh-prod-key`

*(Note : Tous les ID et tokens doivent correspondre à ceux référencés dans votre `Jenkinsfile`)*.

---

## Étape 5 : Lancement du Pipeline de la "Supply Chain" dans Jenkins

1. Sur Jenkins, créez un nouveau projet de type **Pipeline**.
2. Connectez le projet Jenkins au dépôt GitHub hébergeant votre code source.
3. Configurez-le pour utiliser le fichier `Jenkinsfile` présent à la racine.
4. Lancez un Build manuel.

### Que va-t-il se passer durant le pipeline ?
1. **Compilation** de l'App Java (réduction de faille avec le Dockerfile multi-stage).
2. **Scan SonarQube** : Si l'analyse détecte une faille ou trop de code sale, le Quality Gate bloquera la livraison automatique.
3. **Docker Build & Push** : L'image de l'App est compilée avec la référence Harbor, puis poussée vers la registry Harbor.
4. *Harbor exécute automatiquement Trivy (Scan de vulnérabilités sur les paquets et l'OS en tâche de fond)*.
5. **Ansible Deploy** : Le playbook `deploy-app.yml` est envoyé au serveur de Production.
   - Celui-ci télécharge Docker (s'il le faut), la nouvelle Image validée depuis Harbor.
   - Injecte les secrets `DB` et `MAIL` stockés uniquement dynamiquement.
   - S'assure que tout est lancé via `docker-compose`.

---

## Étape 6 : Vérifier le Lancement en Prod

Le pipeline mettra environ une minute pour s'assurer du lancement sécurisé sur le serveur de prod. Patientez sur l'interface Jenkins.

Une fois complété avec succès, parcourez via un navigateur :
```text
http://VOTRE_IP_PROD:8082
```

Votre site dynamique Marketplace devrait s'afficher correctement, branché de base sur la base de données MySQL isolée localement (Aucun accès externe au port 3306) !

> 🎉 **Félicitations**, vous disposez d'un système DevSecOps complet, du commit Git à la plateforme de prod en respectant tous vos prérequis de sécurité !
