FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

COPY target/txt2world-1.0-SNAPSHOT.jar /app/txt2world.jar

EXPOSE 49600

ENTRYPOINT ["java", "-jar", "/app/txt2world.jar"]
