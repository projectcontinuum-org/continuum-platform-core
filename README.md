# Continuum

## Overview
A next-gen truely distributed cloud-native workflow engine for data science and machine learning. Continuum is designed to be a lightweight, easy-to-use, and highly scalable solution for managing complex workflows in a distributed environment.

## Setup dev environment
1. Clone the repository:
   ```bash
    git clone
    cd continuum
    ```
2. Start docker-compose
   ```bash
   cd docker
   docker-compose up -d
   ```
3. Add Search attributes to Temporal server
   ```bash
   temporal operator search-attribute create --name "Continuum:ExecutionStatus" --type "Int"
   temporal operator search-attribute create --name "Continuum:WorkflowFileName" --type "Keyword"
   ```