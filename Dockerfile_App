# Stage 1: Build Custom JRE and Package the App
FROM eclipse-temurin:21-jdk-noble AS builder

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy the project files and build the application
COPY pom.xml .
COPY src ./src

# Run Maven to build the application
RUN mvn clean package -DskipTests=true

# Copy the compiled application to the container
COPY target/api-doc-crafter.jar service.jar

# Create minimal JRE
RUN MODULES=$(jdeps --print-module-deps --ignore-missing-deps --class-path service.jar service.jar | tail -n1) && \
    jlink \
      --add-modules ${MODULES} \
      --strip-native-commands \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --output /custom-jre

# Create executable
RUN mkdir -p jp_tmp && cp service.jar jp_tmp/ && \
    jpackage \
      --input jp_tmp \
      --name service \
      --main-jar service.jar \
      --runtime-image /custom-jre \
      --type app-image

# USED TO EXPORT THE JAR FILE
# [SYSTEM] docker build --target export . --output target
FROM scratch AS export
COPY --from=builder /service /service

# Final Stage: Minimal Runtime Image
FROM debian:stable-slim

# Copy packaged app and additional files
COPY --from=builder /service /service

# Expose the necessary port
EXPOSE 8080

# Use bash for variable substitution and proper PID management
ENTRYPOINT ["/bin/bash", "-c", "exec /service/bin/service"]
