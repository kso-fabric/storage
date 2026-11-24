FROM eclipse-temurin:25-jre
LABEL org.opencontainers.image.source="https://github.com/kso-fabric/storage"

WORKDIR /app

COPY storage-server/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]