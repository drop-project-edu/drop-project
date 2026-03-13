# Development

## Prerequisites

* Java 17-23 JDK (a compiler is needed). Java 24+ is not supported since the SecurityManager was removed (JEP 486). If using Java 18-23, the JVM flag `-Djava.security.manager=allow` must be set.
* Maven
* MySQL 8.x (optional — H2 in-memory is used by default; MySQL is recommended for persistent data)

## Building from Source

After cloning the repository, change the following properties:

    dropProject.maven.home=<path_to_maven_home>
    dropProject.maven.repository=<path_to_maven_repository>

in the following files:

     src/main/resources/drop-project.properties
     src/test/resources/drop-project-test.properties

Note:

* Configuring the first file will allow you to run Drop Project.
* Configuring the second file will allow you to run Drop Project's unit tests.
* Linux users: the Maven Home is usually in `/usr/share/maven` (otherwise, try: `whereis maven`) and the Maven Repository is in `/home/<USERNAME>/.m2`
* Windows users: the Maven Home is the installation directory and the Maven Repository is in `c:\Users\<USERNAME>\.m2`

And run the embedded tomcat runner:

    mvn spring-boot:run

The application should now be accessible on [http://localhost:8080](http://localhost:8080)

## Running with MySQL

By default, Drop Project uses an H2 in-memory database, so MySQL is not required to get
started. If you want persistent data, you can run MySQL via Docker:

    docker run -d --name dp-mysql \
      -e MYSQL_ROOT_PASSWORD=secret \
      -e MYSQL_DATABASE=dp_dev \
      -e MYSQL_USER=dp_dev \
      -e MYSQL_PASSWORD=dp123 \
      -p 3306:3306 \
      -v dp-mysql-data:/var/lib/mysql \
      mysql:8.0.35 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci

Then start Drop Project with the `mysql` profile:

    mvn spring-boot:run -Dspring-boot.run.profiles=mysql

Data persists across container restarts. Use `docker stop dp-mysql` and `docker start dp-mysql`
to stop/start the container without losing data.

## Running with Docker (building from sources)

    docker compose -f deploy/docker-compose-dev.yml up

## Running on GitHub Codespaces

You can run Drop Project on [GitHub Codespaces](https://github.com/features/codespaces) by clicking the button below:

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/drop-project-edu/drop-project)

This will open a new codespace with Drop Project already cloned.

To run Drop Project, open a terminal in the codespace and in the root directory execute:

    docker compose -f deploy/docker-compose-dev.yml up

Once the server is running, you can access the application using either of the following methods:

* Navigate to the Ports tab (located next to the Terminal tab in the bottom panel). Find port 8080, hover over the "Local Address" column, and click the globe icon (Open in Browser).

* Hold Ctrl (or Cmd on macOS) and click the link http://localhost:8080 directly inside the Codespace.

## Logging

When running with the `dev` Spring profile, logs are printed **only to the console** (no
file). In production, logs are written only to a rolling file and the console is disabled.

## Unit Tests

To execute the unit tests, run:

    mvn test

## Unit Test Coverage

To measure the unit test coverage, run:

    mvn jacoco:report

Note: to run the coverage measurement, you must first run the unit tests themselves.

The test coverage report will be available in the following folder:

    target/site/jacoco/index.html

## Generating Documentation

To generate source code documentation, run:

    mvn dokka:dokka

The generated documentation will be available in the following folder:

    target/dokka/drop-project/index.html

Full documentation: [https://drop-project-edu.github.io/drop-project/](https://drop-project-edu.github.io/drop-project/)
