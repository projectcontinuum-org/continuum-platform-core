# Use the official image as a parent image
FROM mcr.microsoft.com/vscode/devcontainers/java:21

# Install Gradle
RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-7.5.1-bin.zip -P /tmp && \
    unzip -d /opt/gradle /tmp/gradle-7.5.1-bin.zip && \
    ln -s /opt/gradle/gradle-7.5.1/bin/gradle /usr/bin/gradle

# Install Node.js and Yarn
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g yarn

# Set the default shell to bash
SHELL ["/bin/bash", "-c"]

# Set the working directory
WORKDIR /workspace