# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --uid 1001 armazem
COPY --from=build /app/target/armazem-ingredientes-*.jar app.jar
# Console H2: login já preenchido com o banco da aplicação (jdbc:h2:mem:armazem).
COPY --chown=armazem docker/h2.server.properties /home/armazem/.h2.server.properties
USER armazem
# Perfil "docker": libera o Console H2 para acesso pelo host (ver application-docker.yml).
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
