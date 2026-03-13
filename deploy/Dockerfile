FROM maven:3.8.5-openjdk-17-slim AS build

WORKDIR /usr/src/app

COPY pom.xml .

RUN mvn clean package -Dmaven.main.skip -Dmaven.test.skip -DskipTests

COPY src src/

RUN mvn clean package -DskipTests

FROM openjdk:17.0.2-slim

EXPOSE 8080

WORKDIR /usr/src/app

RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

ENV DP_M2_HOME=/usr/share/maven
ENV DP_MVN_REPO=/usr/src/app/mvn_repository

COPY --from=build /usr/src/app/target/drop-project.jar /usr/src/app/

CMD ["java", "-jar", "drop-project.jar"]