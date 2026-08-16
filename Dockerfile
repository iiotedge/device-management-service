# syntax=docker/dockerfile:1.7
#
# Runtime-only image: the jar is built beforehand by CI (`mvn verify`), not
# inside this Dockerfile - same pattern as auth-service/device-profile-service.
# A real Maven build here would need network access to GitHub Packages
# (iiotedge-parent/iiotedge-bom) *and* the separate `common` repo's data/base/
# utils modules built from source, neither of which are part of this build
# context. Building outside Docker and packaging the already-tested artifact
# keeps the test/SpotBugs gate a real gate in CI, not something skipped to
# make an in-container build tractable.

# ---- Extract stage: split the fat jar into layers for better caching ----
FROM gcr.io/distroless/java21-debian12:nonroot AS builder

WORKDIR /app
COPY target/device-management-service.jar /app/app.jar

# Spring Boot layered jars extract faster and give Docker a better layer
# cache than copying one fat jar - dependency layers (the vast majority of
# the jar's bytes) only invalidate when dependencies actually change, not
# on every code change.
RUN ["java", "-Djarmode=tools", "-jar", "/app/app.jar", "extract", "--layers", "--launcher", "--destination", "/app/extracted"]

# ---- Runtime stage ----
# Google's distroless Java 21 (Debian 12), nonroot variant - no shell, no
# package manager, no coreutils, minimal CVE surface, and it already runs
# as a non-root user by default (no separate USER instruction needed).
FROM gcr.io/distroless/java21-debian12:nonroot

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG VERSION=unknown

LABEL org.opencontainers.image.title="device-management-service" \
      org.opencontainers.image.description="IoTMining Device Management Service" \
      org.opencontainers.image.source="https://github.com/iiotedge/device-management-service" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# Container-aware heap sizing (not a hardcoded -Xmx), a modern low-pause
# collector, UTF-8/UTC defaults so behavior doesn't depend on the host, and
# /dev/./urandom so TLS/JWT verification doesn't block on kernel entropy the
# way /dev/random can under load - a well-known JVM-in-a-container gotcha.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8055

EXPOSE 8055

# No Docker-level HEALTHCHECK: distroless has no shell/curl/wget to run one
# from. /actuator/health (already exposed) is meant to be probed by the
# orchestrator instead - a Kubernetes readiness/liveness probe hitting that
# endpoint directly over HTTP, not an in-container command.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
