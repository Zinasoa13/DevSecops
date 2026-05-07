# Étape 1 : Build de l'application avec Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
# Copier le fichier pom.xml et télécharger les dépendances (cache Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copier le code source et compiler
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2 : Création de l'image d'exécution allégée
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Créer un utilisateur non-root pour plus de sécurité
RUN groupadd -r spring && useradd -r -g spring springuser
# Création d'un dossier pour les QR Codes persistant
RUN mkdir -p /app/qrcodes && chown springuser:spring /app/qrcodes

# Copier le jar compilé depuis l'étape précédente
COPY --from=builder /app/target/marketplace-0.0.1-SNAPSHOT.jar app.jar
RUN chown springuser:spring app.jar

USER springuser

# Variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=prod
ENV DB_HOST=marketplace-db
ENV DB_PORT=3306

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]