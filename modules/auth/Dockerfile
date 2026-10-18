# Build context must be the repo root (see docker-compose.yml's `modauth`
# service: `context: .`, `dockerfile: modules/auth/Dockerfile`) — this
# module's settings.gradle pulls in lib/Gen_Auth-main as a composite build,
# so both directories have to be present in the same relative layout for
# `gradle bootJar` to resolve `com.example:gen-auth-starter`.

FROM gradle:9.7.1-jdk21 AS build
WORKDIR /workspace
COPY lib/Gen_Auth-main lib/Gen_Auth-main
COPY modules/auth modules/auth
WORKDIR /workspace/modules/auth
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/modules/auth/build/libs/modules-auth.jar app.jar
EXPOSE 8102
ENTRYPOINT ["java", "-jar", "app.jar"]
