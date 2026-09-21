# The API image. Two stages: Gradle builds the jar, the runtime carries only a
# JRE and that jar.
#
# The build is not a pre-built jar copied from the host, because `docker
# compose up` has to work from a fresh clone and `api/build/` is gitignored.
# It also reproduces better: the JDK is pinned in the FROM line below and the
# Gradle version comes from the committed wrapper, whereas a host build
# inherits whatever `brew install openjdk@21` happened to give the developer.
#
# Every base image is pinned by tag *and* digest. A tag like `21-jre` floats:
# it is a different image next month, so the tag alone does not say what was
# built.

FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.24@sha256:cd87715a8d45cfaa42419207c64680234f62785c49055cccd20437b5c9018380 AS build

WORKDIR /workspace

# Dependency resolution is its own layer, keyed on the build files alone, so
# every pull request that does not change dependencies reuses it.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src src
# Only bootJar: tests belong to the `API checks` job, which runs them once on
# every pull request rather than once per image build.
RUN ./gradlew --no-daemon bootJar && mv build/libs/*.jar build/app.jar

FROM eclipse-temurin:21.0.12_8-jre-alpine-3.24@sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52 AS runtime

# Alpine rather than the Ubuntu-based variant for one reason: HEALTHCHECK needs
# an HTTP client, and busybox already provides wget. The Ubuntu image ships
# neither wget nor curl, so it would need an apt layer for the probe alone.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/app.jar app.jar

# Numeric, not `USER app`. The runAsNonRoot checks in ECS and Kubernetes read
# the uid out of the image configuration and cannot resolve a name against the
# image's own /etc/passwd, so a named user fails them on a correct image.
USER 10001:10001

EXPOSE 8080

# start-period covers Flyway plus the Spring context; a failure before it
# elapses does not count against retries.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

# Exec form, so the JVM is PID 1 and receives SIGTERM directly. A process
# sitting in front of it that does not forward the signal makes every stop wait
# out the whole grace period and end in SIGKILL, dropping in-flight requests:
# measured on this image at the full 30s, against 1s here.
#
# The usual warning is that `ENTRYPOINT java -jar ...` puts /bin/sh at PID 1.
# On this base it does not: busybox ash exec's the last command of an `sh -c`
# instead of forking it, so the JVM still ends up at PID 1. That is a shell
# implementation detail rather than a guarantee -- a wrapper script that
# backgrounds the JVM leaves sh at PID 1 and reproduces the 30s hang exactly.
# The exec form depends on none of it, and the CI job asserts the property
# rather than the syntax: PID 1 is java, and the container stops promptly.
#
# No tini. Its job is reaping orphaned children and the JVM forks none here;
# the JVM installs its own SIGTERM handler, which Spring Boot's graceful
# shutdown hooks into.
#
# MaxRAMPercentage because the JVM's default maximum heap under a cgroup limit
# is 25%: a 512 MB task would otherwise run on a 128 MB heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
