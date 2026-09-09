# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy the POM first so dependency resolution is cached independently of source changes.
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/target/milk-collection-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
