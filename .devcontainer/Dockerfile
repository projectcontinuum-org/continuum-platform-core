# Use the official image as a parent image
FROM mcr.microsoft.com/vscode/devcontainers/java:21-bookworm

# Install Gradle
RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-7.5.1-bin.zip -P /tmp && \
    unzip -d /opt/gradle /tmp/gradle-7.5.1-bin.zip && \
    ln -s /opt/gradle/gradle-7.5.1/bin/gradle /usr/bin/gradle

# Install Node.js, Yarn, pkg-config, and libsecret-1-dev
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs pkg-config libsecret-1-dev

# Set the default shell to bash
SHELL ["/bin/bash", "-c"]

# Set the working directory
WORKDIR /workspace