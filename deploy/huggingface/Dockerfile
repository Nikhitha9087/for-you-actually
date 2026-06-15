# Hugging Face Space Dockerfile.
# The Space repo stays tiny: this file pulls everything (code + the prebuilt 59 MB
# catalogue) from GitHub at build time, so the large H2 file never goes through HF's
# git (which would otherwise require Git LFS). Trigger a "factory rebuild" on the Space
# to redeploy after pushing changes to GitHub main.

FROM alpine/git AS src
RUN git clone --depth 1 https://github.com/Nikhitha9087/for-you-actually.git /src

# 1) Build the Angular production bundle
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY --from=src /src/frontend/ ./
RUN npm ci && npm run build

# 2) Build the Spring Boot jar, bundling the Angular bundle as static resources
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /be
COPY --from=src /src/backend/pom.xml ./
COPY --from=src /src/backend/src ./src
COPY --from=frontend /fe/dist/frontend/browser/ ./src/main/resources/static/
RUN mvn -q -DskipTests clean package

# 3) Minimal runtime (HF runs the container as uid 1000)
FROM eclipse-temurin:17-jre
# The base image already has a user with UID 1000 (the one HF runs as), so we reuse
# it rather than creating one. /app is chowned to that UID so H2's lock/trace files
# are writable at runtime.
WORKDIR /app
COPY --from=backend /be/target/*.jar app.jar
COPY --from=src /src/backend/data/fya.mv.db ./data/fya.mv.db
RUN chown -R 1000:1000 /app
USER 1000
ENV HOME=/app
ENV JAVA_OPTS="-Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
