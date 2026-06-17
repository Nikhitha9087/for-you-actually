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
# libgomp1 is required by the PyTorch native library that powers the local embedding model.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*
# The base image already ships a user with UID 1000 (the uid HF runs as), so we reuse
# it instead of creating one. /app is chowned to that UID so the baked H2 file, its
# lock/trace files, and DJL's model cache ($HOME/.djl.ai) are writable at runtime.
WORKDIR /app
COPY --from=backend /be/target/*.jar app.jar
COPY backend/data/fya.mv.db ./data/fya.mv.db
RUN chown -R 1000:1000 /app
USER 1000
ENV HOME=/app
# The local embedding model (DJL) downloads its weights + PyTorch native on first start
# into $HOME/.djl.ai; heap is modest since the 384-dim index is tiny.
ENV JAVA_OPTS="-Xmx1g"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
