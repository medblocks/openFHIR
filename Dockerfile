# java build image, java 17
FROM maven:3.8.3-openjdk-17-slim AS build
WORKDIR /app
COPY . .
# build without tests
RUN mvn clean package -DskipTests

# Path: Dockerfile
# java runtime image, java 17
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]