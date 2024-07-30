<img width="77px" height="77px" align="right" src="docs/dp_logo.png"/>

# Drop Project - Continuous Auto-Grader

![Build Status](https://github.com/drop-project-edu/drop-project/workflows/Run%20Tests/badge.svg?branch=master)
[![codecov](https://codecov.io/gh/drop-project-edu/drop-project/branch/master/graph/badge.svg)](https://codecov.io/gh/drop-project-edu/drop-project)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Docker Image Version](https://img.shields.io/docker/v/pedroalv3s/drop-project?label=docker&link=https%3A%2F%2Fhub.docker.com%2Fr%2Fpedroalv3s%2Fdrop-project)](https://hub.docker.com/r/pedroalv3s/drop-project)


A web application where students drop their projects to check for correctness and quality.

Several checks are performed on each project:

* Does it have the right folder structure and all the mandatory files?
* Does it compile?
* Does it pass its own tests?
* Does it achieve a minimum coverage with its own tests?
* Does it pass the teacher tests?
* Does it conform to the coding guidelines defined by the teacher?
* Does it perform well? (tests with large datasets are measured)

## Requirements

* Java 17+ JDK (a compiler is needed)
* Maven
* Servlet Engine (Tested with tomcat and jetty)
* MySQL

## Current limitations

* Only works for Java 8+ / Kotlin non-maven projects

## How does it work?

Most of the work is done by a Maven Invoker, running several goals on the project.
The results are collected into a report that is viewable by the student and the teacher.

Projects must not be maven projects because they are "mavenized" by the server, after uploading.
In this context, "mavenizing", means copying the files into a proper Maven folder structure (e.g. putting the sources
into `/src/main/java`), mixing the student files with the teacher unit tests and adding a `pom.xml`
(also provided by the teacher).

Since checking a project may take some time, it is done asynchronously - the student submits the file and must come
back later to check the report.

![How DP works](docs/how_dp_works.png)

This video explains how to create a simple Java assignment in Drop Project. It shows both teacher and student perspectives:

https://www.youtube.com/watch?v=65IwIBuMDlE

## Quick start

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

## Running with docker

### Demo mode (in memory database)

    docker run -p 8080:8080 pedroalv3s/drop-project

### Production mode (using mysql)

Clone the project and in the root folder execute:

    docker compose up

The application should now be accessible on [http://localhost:8080](http://localhost:8080)

## Documentation

[https://drop-project-edu.github.io/drop-project/](https://drop-project-edu.github.io/drop-project/)

To generate source code documentation, run:

    mvn dokka:dokka

The generated documentation will be available in the following folder:

    target/dokka/drop-project/index.html

## Unit Tests

To execute the unit tests, run:

    mvn test

## Unit Test Coverage

To measure the unit test coverage, run:

    mvn jacoco:report

Note: to run the coverage measurement, you must first run the unit tests themselves.

The test coverage report will be available in the following folder:

    target/site/jacoco/index.html
    
## API (experimental)

Some services are accessible through an API, protected by personal tokens. Still work in progress...

Documentation: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

## Citation

* Cipriano, B.P., Fachada, N. & Alves, P. (2022). Drop Project: An automatic assessment tool for programming assignments. *SoftwareX*, 18. 101079. <https://doi.org/10.1016/j.softx.2022.101079>
