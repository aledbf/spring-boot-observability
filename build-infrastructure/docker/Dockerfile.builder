# Builder image with exact JDK version for consistent builds
# Use this for local development and CI to ensure identical behavior

ARG JDK_VERSION=24
FROM eclipse-temurin:${JDK_VERSION}-jdk-alpine

LABEL org.opencontainers.image.title="Java Build Environment"
LABEL org.opencontainers.image.description="Consistent Java build environment for all services"

# Install required tools
RUN apk add --no-cache \
    bash \
    curl \
    git \
    docker-cli \
    && rm -rf /var/cache/apk/*

# Maven version - pinned for reproducibility
ARG MAVEN_VERSION=3.9.6
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Install Maven
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar -xzC /opt \
    && mv /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} \
    && ln -s ${MAVEN_HOME}/bin/mvn /usr/bin/mvn

# Create non-root user for builds
RUN addgroup -g 1000 builder && \
    adduser -u 1000 -G builder -h /home/builder -D builder

# Create Maven cache directory
RUN mkdir -p /home/builder/.m2/repository && \
    chown -R builder:builder /home/builder

# Default Maven settings (can be overridden via volume mount)
COPY --chown=builder:builder settings.xml /home/builder/.m2/settings.xml

USER builder
WORKDIR /workspace

# Verify installation
RUN java --version && mvn --version

# Default command
ENTRYPOINT ["/bin/bash", "-c"]
CMD ["mvn --version"]
