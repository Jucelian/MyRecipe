# Use the official Gradle image to build the project
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Build the server shadow jar (or executable)
# Note: Using :server:installDist or similar depending on the application plugin
RUN ./gradlew :server:installDist --no-daemon

# Use a lightweight JRE image for the final run
FROM openjdk:17-slim
EXPOSE 8080
RUN mkdir /app
RUN mkdir /app/data
COPY --from=build /home/gradle/src/server/build/install/server /app/
WORKDIR /app/bin
CMD ["./server"]
