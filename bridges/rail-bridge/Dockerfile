# --- Build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Purge des fichiers vides / marqueurs d'échec laissés par un téléchargement interrompu
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    find /root/.m2 -type f \( -size 0 -o -name '*.lastUpdated' \) -delete ; \
    mvn -q -B -DskipTests package

# --- Runtime ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/rail-bridge-0.1.0.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]