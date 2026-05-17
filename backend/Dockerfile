FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-noble

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

RUN apt-get update && apt-get install -y curl

ENTRYPOINT ["java", "-jar", "app.jar"]