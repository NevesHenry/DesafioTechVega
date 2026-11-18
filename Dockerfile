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
RUN useradd --system --uid 1001 armazem
COPY --from=build /app/target/armazem-ingredientes-*.jar app.jar
USER armazem
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
