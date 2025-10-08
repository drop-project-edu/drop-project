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
    
## API

Some services are accessible through an API, protected by personal tokens.

Documentation: [https://playground.dropproject.org/dp/swagger-ui.html](https://playground.dropproject.org/dp/swagger-ui.html)

## MCP (experimental)

Drop Project can act an [MCP](https://modelcontextprotocol.io/) server. Right now, only some operations are permitted and only for teachers.

Just connect to `https://<server-url>/mcp/` with an Authorization header: `Bearer <your-personal-token>`.

### Using claude code

    claude mcp add --transport http drop-project https://playground.dropproject.org/dp/mcp/ --header "Authorization: Bearer xxxxx" (replace xxxxx with your personal token)

### Using github copilot plugin in Intellij

* Enter the chat window
* Select "agent" mode
* Click the tool icon, next to the dropdown with the models
* Click the "add more tools" button
* It will open an mcp.json file. Add the following content:
```json
"servers": {
  "drop-project": {
  "url": "https://playground.dropproject.org/dp/mcp/",
  "requestInit": {
    "headers": {
      "Authorization": "Bearer xxxx" (replace xxxxx with your personal token)
    }
  }
}
```

## Plugin

There is a [plugin for Intellij](https://plugins.jetbrains.com/plugin/25078-drop-project) that allows students to submit their projects directly from the IDE.

## Playground

You can experiment with Drop Project on a public cloud instance. 
You'll have to authenticate using your github credentials. 
By default, you'll be assigned the student role. 
If you wish to experiment with the teacher role, send me an email and I'll be happy to assign you that role.

[Playground](https://playground.dropproject.org/)

## Citation

* Cipriano, B.P., Fachada, N. & Alves, P. (2022). Drop Project: An automatic assessment tool for programming assignments. *SoftwareX*, 18. 101079. <https://doi.org/10.1016/j.softx.2022.101079>
* Cipriano, B.P., Baltazar, B., Fachada, N., Vourvopoulos, A., & Alves, P. (2024). Bridging the Gap between Project-Oriented and Exercise-Oriented Automatic Assessment Tools. *Computers*, 13(7), 162. <https://www.mdpi.com/2073-431X/13/7/162>