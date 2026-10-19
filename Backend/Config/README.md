# Config Server

Spring Cloud Config Server for the banking platform.

## What it does

- Starts on port `8888`
- Reads configuration from the GitHub repository configured in `src/main/resources/application.yml`
- Serves config from the `config` branch by default

## Prerequisites

- Java 21
- Git access to the GitHub repository
- Optional: `CONFIG_REPO_URI`, `CONFIG_REPO_BRANCH`, `CONFIG_REPO_USERNAME`, `CONFIG_REPO_PASSWORD`

## Local run

```bash
cd Backend/Config
mvnw.cmd test
mvnw.cmd spring-boot:run
```

## Environment variables

If you want to override the Git source without editing `application.yml`, set:

- `CONFIG_REPO_URI` - defaults to `https://github.com/ziadabdelnaby10/Banking-System-Application.git`
- `CONFIG_REPO_BRANCH` - defaults to `config`
- `CONFIG_REPO_USERNAME` - optional, for private repos
- `CONFIG_REPO_PASSWORD` - optional, for private repos

## Example endpoint

Once the server is running, requests will look like:

```text
http://localhost:8888/<application-name>/<profile>
```

Example:

```text
http://localhost:8888/customer-service/dev
```

