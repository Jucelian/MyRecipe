# Use the official Gradle image to build the project
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Fix permissions for gradlew
RUN chmod +x gradlew

# Build the server shadow jar (or executable)
RUN ./gradlew :server:installDist --no-daemon

# Use Eclipse Temurin as the base image for the final run
# OpenJDK official images are deprecated, Temurin is the recommended alternative
FROM eclipse-temurin:17-jre-alpine
EXPOSE 8080
RUN mkdir /app
RUN mkdir /app/data
COPY --from=build /home/gradle/src/server/build/install/server /app/
WORKDIR /app/bin
CMD ["./server"]
