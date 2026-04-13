FROM gradle:8.10-jdk17 AS framework-build
WORKDIR /workspace/consumer-driven-contract-testing
COPY . .
RUN gradle --no-daemon clean test publishToMavenLocal --stacktrace

FROM gradle:8.10-jdk17 AS framework-runner
WORKDIR /workspace/consumer-driven-contract-testing
COPY . .
CMD ["gradle", "--no-daemon", "clean", "test", "--stacktrace"]

