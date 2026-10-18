# Build context must be the repo root — see docker-compose.yml's `tenant`
# service (`context: .`, `dockerfile: modules/tenant/Dockerfile`). Same
# reasoning as modules/auth/Dockerfile: settings.gradle pulls in
# lib/Gen_Tenant-main as a composite build.

FROM gradle:9.7.1-jdk21 AS build
WORKDIR /workspace
COPY lib/Gen_Tenant-main lib/Gen_Tenant-main
COPY modules/tenant modules/tenant
WORKDIR /workspace/modules/tenant
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/modules/tenant/build/libs/modules-tenant.jar app.jar
EXPOSE 8103
ENTRYPOINT ["java", "-jar", "app.jar"]
