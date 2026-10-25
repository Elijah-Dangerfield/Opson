# ---------------------------------------------------------------------------
# Build stage: a full Gradle build of just :apps:server, producing the
# installDist output (a bin script + lib jars). Temurin 17 matches the server's
# `kotlin { jvmToolchain(17) }`.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Build scaffolding first so Docker caches it across source-only changes.
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY versions.properties gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./

# Only the server module is needed. `-DserverOnly=true` (see
# settings.gradle.kts) trims the build graph so no Android/iOS toolchain is
# required. If apps/server gains a :libraries:* dep, COPY it here too.
COPY apps/server/ apps/server/

# CI=true skips the git-hooks check (there's no .git in the build context anyway);
# it's a rename-proof guard. in-process Kotlin compiler keeps the build to a single
# JVM (Gradle + a forked Kotlin daemon together can OOM-kill a small build container).
ENV CI=true
RUN chmod +x gradlew && \
    ./gradlew :apps:server:installDist \
        --no-daemon \
        --console=plain \
        --build-cache \
        -DserverOnly=true \
        -Pkotlin.compiler.execution.strategy=in-process

# ---------------------------------------------------------------------------
# Runtime stage: JRE-only. JAVA_OPTS is declared here so fly.toml's [env] can
# override it.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/apps/server/build/install/server/ /app/

# The hosted admin console. The deploy workflow builds the apps/admin JS
# bundle on the runner and copies it into apps/server/admin-web/ before
# the image build, so this COPY takes it from the build context — the Gradle
# build above stays JS-toolchain-free on purpose. An empty dir (only .gitkeep)
# is fine: the server just skips serving /admin.
COPY apps/server/admin-web/ /app/admin-web/
ENV ADMIN_WEB_DIR=/app/admin-web

ENV JAVA_OPTS=""
ENV SERVER_PORT=8080
EXPOSE 8080

# `bin/server` is the start script Gradle's application plugin generates.
CMD ["/app/bin/server"]
