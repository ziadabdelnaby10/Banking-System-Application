# CI/CD Setup

This backend folder now contains a minimal GitHub Actions pipeline and Docker image build setup.

## What runs in CI

- Checkout and JDK 21 setup.
- PostgreSQL and Redis service containers.
- Maven or Gradle build, depending on which wrapper is present.
- Docker image build to catch containerization issues early.

## What runs in CD

- Build and publish the backend image to GitHub Container Registry.
- Tag images by branch, commit SHA, and release tag.
- Provide a manual deployment entry point for your target runtime.

## Required repository secrets

- `GITHUB_TOKEN` is provided by GitHub Actions for package publication.
- Add deployment secrets only if you wire the deploy job to a real target:
  - `DEPLOY_HOST`
  - `DEPLOY_USER`
  - `DEPLOY_KEY`
  - `DEPLOY_PATH`

## Required local files

- A Maven or Gradle build in the backend root.
- For Maven, keep `mvnw`, `.mvn/`, and `pom.xml` in place.
- For Gradle, keep `gradlew` and the Gradle wrapper files in place.

## Notes

- The Dockerfile assumes a Spring Boot executable JAR is produced in `target/`.
- If you use Gradle, adjust the Dockerfile build stage to match your output directory.
- The deploy job is intentionally a template because the target infrastructure has not been specified yet.
