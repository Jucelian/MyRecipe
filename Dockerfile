# Use the official Gradle image to build the project
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Fix permissions and line endings for gradlew
RUN chmod +x gradlew && \
    sed -i 's/\r$//' gradlew

# Build the server shadow jar (or executable)
ENV SERVER_ONLY=true
RUN ./gradlew :server:installDist --no-daemon --no-configuration-cache

# Use Eclipse Temurin as the base image for the final run
FROM eclipse-temurin:17-jre-alpine
EXPOSE 8080
ENV DATABASE_PATH=/app/data/recipes
RUN mkdir -p /app/data
COPY --from=build /home/gradle/src/server/build/install/server /app/
WORKDIR /app/bin
CMD ["./server"]
