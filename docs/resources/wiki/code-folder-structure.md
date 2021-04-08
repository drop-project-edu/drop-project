Drop Project's code itself follows the Maven folder structure. 

The following figure represents DP's folder/directory structure. Below the figure, a description of
 the contents of each sub-folder is given.

    . src
    +-- main
    +-- kotlin
    +---- org.dropProject
    +------ controllers
    +------ dao 
    +------ data
    +------ extensions
    +------ filters
    +------ repositories
    +------ security
    +------ services
    +-- test
    +---- kotlin
    +---- org.dropProject
    +------ controllers
    +------ dao
    +------ data
    +------ ...
    +------ other test folders
    
#### src/main/kotlin/drop-project/controllers
Contains code related with handling HTTP requests.

#### src/main/kotlin/drop-project/dao
Contains the Data Access Object classes. This is where you will find the Assignment and Submission classes.
                  
#### src/main/kotlin/drop-project/data
Contains auxiliary classes used in session.

#### src/main/kotlin/drop-project/extensions
Code that extends certain classes of the Java API (e.g. Date).

#### src/main/kotlin/drop-project/filters
Classes that intercept and pre-process HTTP requests.

#### src/main/kotlin/drop-project/repositories
Code that defines functions/interfaces to find persisted objects.

#### src/main/kotlin/drop-project/security
Contains access control definitions.

#### src/main/kotlin/drop-project/services
Business logic code.

#### src/test/kotlin/drop-project/controllers
Contains tests for controllers.

#### src/test/kotlin/drop-project/dao
Contains tests for Data Access Object classes.

#### src/test/kotlin/drop-project/data
Contains tests for the auxiliary classes .

#### src/test/kotlin/drop-project/...
Tests for other packages.
