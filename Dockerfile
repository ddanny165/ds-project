FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/IOTCameras-0.0.1.jar app.jar
COPY videos /app/videos

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
