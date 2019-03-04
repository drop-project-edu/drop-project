[![Build Status](https://travis-ci.org/drop-project-edu/drop-project.svg?branch=master)](https://travis-ci.org/drop-project-edu/drop-project)

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy?template=https://github.com/palves-ulht/drop-project)

# Drop Project - Continuous Auto-Grader

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

* Java 8 JDK (a compiler is needed)
* Maven
* Servlet Engine (Tested with tomcat and jetty)
* MySQL

## Current limitations

* Only works for Java 8 / Kotlin non-maven projects

## How does it work?

Most of the work is done by a Maven Invoker, running several goals on the project. 
The results are collected into a report that is viewable by the student and the teacher.

Projects must not be maven projects because they are "mavenized" by the server, after uploading. 
By "mavenizing", I mean copying the files into a proper Maven folder structure (e.g. putting the sources 
into /src/main/java), mixing the student files with the teacher unit tests and adding a pom.xml 
(also provided by the teacher). 

Since checking a project may take some time, it is done asynchronously - the student submits the file and must come 
back later to check the report.

## FAQ

**How do I change the max upload file size?**

Check dropProject.properties.

**How to get coverage metrics for student unit testes?**

Add the following lines to your pom.xml:
```
<properties>
    <dp.argLine></dp.argLine>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.19.1</version>
    <configuration>
        <argLine>@{argLine} ${dp.argLine}</argLine>
    </configuration>
</plugin>

<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.2</version>
    <configuration>
        <includes>
            <include>edu/someUniversity/xxx/xxx/*</include>
        </includes>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>generate-code-coverage-report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```