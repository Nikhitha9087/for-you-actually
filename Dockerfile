# syntax=docker/dockerfile:1
# Single-artifact image: Angular bundle is built, baked into Spring Boot's static
# resources, and served by the same JVM. Catalogue (H2) is baked in so it runs with no keys.

# 1) Build the Angular production bundle
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Build the Spring Boot jar, bundling the Angular bundle as static resources
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /be
COPY backend/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /fe/dist/frontend/browser/ ./src/main/resources/static/
RUN mvn -q -DskipTests clean package

# 3) Minimal runtime
FROM eclipse-temurin:17-jre
# Hugging Face Spaces run the container as uid 1000; create a matching user so the
# baked H2 file and its lock/trace files are writable at runtime.
RUN useradd -m -u 1000 appuser
WORKDIR /app
COPY --from=backend /be/target/*.jar app.jar
COPY backend/data/fya.mv.db ./data/fya.mv.db
RUN chown -R appuser:appuser /app
USER appuser
# Cap the heap so it fits comfortably on small free instances.
ENV JAVA_OPTS="-Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
