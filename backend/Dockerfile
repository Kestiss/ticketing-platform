FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts ./
COPY backend backend
RUN ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
