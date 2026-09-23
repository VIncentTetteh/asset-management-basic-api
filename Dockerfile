# AssetIQ backend image.
#
# Two stages: a full Maven + JDK 21 builder, and an Alpine JRE runtime that
# carries no compiler, no build tools and no package manager state.
#
# Why Alpine JRE and not distroless:
#   distroless/java21 has no shell and no busybox, so it cannot run a container
#   HEALTHCHECK, and `docker compose` has no way to express "wait until the
#   backend is actually serving" without one. The self-hosted compose file
#   depends on that healthcheck to order Flyway against Postgres and to gate the
#   web tier. eclipse-temurin:21-jre-alpine is ~60 MB larger, has no OS package
#   manager in the final layer, and gives us busybox wget for the probe. If you
#   would rather have distroless, drop the HEALTHCHECK here and move liveness to
#   the orchestrator (the Helm chart already probes /actuator/health/liveness and
#   does not rely on this HEALTHCHECK at all).

# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# A BuildKit cache mount on the local repository, rather than a
# `dependency:go-offline` layer.
#
# go-offline resolves every plugin's dependencies for every lifecycle phase,
# which for this POM is several gigabytes, far more than `package` actually
# needs — and it is prone to stalling partway through, leaving a build that
# looks alive at 0% CPU. The cache mount is both smaller and faster: `package`
# fetches only what it uses, and the cache survives across builds on this
# machine, so a source-only change re-resolves nothing.
#
# The cache is not baked into any layer, so the published image carries no
# build-time artefacts.
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -DskipTests package \
 && cp target/assetIQ-*.jar /build/app.jar

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# OCI metadata. Version is stamped by the publish tooling (make ecr-push);
# it deliberately has no default that looks like a release.
ARG VERSION=0.0.0-dev
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.title="AssetIQ Backend" \
      org.opencontainers.image.description="AssetIQ enterprise asset management API" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.licenses="Proprietary"

# Fixed uid/gid so a host bind-mounted uploads directory has a predictable owner
# and so the Kubernetes securityContext (runAsUser: 10001) matches the image.
RUN addgroup -S -g 10001 assetiq \
 && adduser  -S -u 10001 -G assetiq -h /app -s /sbin/nologin assetiq

WORKDIR /app
COPY --from=build --chown=10001:10001 /build/app.jar /app/app.jar

# Local file storage target. Mounted over by a named volume in compose; created
# here so the directory exists and is writable even when it is not mounted.
RUN mkdir -p /app/uploads && chown 10001:10001 /app/uploads

USER 10001:10001
EXPOSE 8080

# The image is designed to run with `read_only: true` / readOnlyRootFilesystem.
# Everything the JVM writes must live under one of these paths, all of which the
# runtime supplies as tmpfs or a volume:
#   /tmp         — JVM hsperfdata, Tomcat multipart spool, Flyway scratch
#   /app/uploads — user uploads (named volume / PVC)
VOLUME ["/tmp", "/app/uploads"]

# busybox wget: no curl in this image by design. /actuator/health/liveness is
# permitted unauthenticated and does not touch the database, so a Postgres blip
# cannot turn into a container restart loop.
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/liveness || exit 1

# -XX:+UseContainerSupport      respect cgroup memory/CPU limits
# -XX:MaxRAMPercentage=75       leave headroom for metaspace and thread stacks
# -XX:+ExitOnOutOfMemoryError   die instead of limping, so the orchestrator restarts us
# -Djava.security.egd           avoid blocking on /dev/random at startup
# -Djava.io.tmpdir=/tmp         explicit, because the root filesystem is read-only
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.io.tmpdir=/tmp", \
  "-jar", "/app/app.jar"]
