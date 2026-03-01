# Stage 1: build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn package -DskipTests -q

# Stage 2: run
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/bank2budget-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
