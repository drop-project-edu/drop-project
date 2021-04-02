This tutorial explains how a teacher can create a new Assignment in Drop Project.

The first step is to create a git repository with the correct structure. The easiest way to do this is to clone one of the the following repositories:

https://github.com/drop-project-edu/sampleJavaAssignment

https://github.com/drop-project-edu/sampleKotlinAssignment

# Structure

The structure of the repository must be the following:

    + assignment
    |--- pom.xml  (includes the checkstyle and junit plugins)
    |--- checkstyle.xml  (the rules to validate the code style, e.g., variables must start with lowercase)
    |--- instructions.html     (optional, the assignment instructions)
    |--- src
    |------ main
    |--------- ... (here goes the reference implementation, in maven structure)
    |------ test
    |--------- ... (JUnit tests that will validate students' submissions)

After the creation of the repository, the following steps should be followed in the Drop Project web-application:

1. Login with your teacher account
2. From the top menu, open the `Manage Assignments` page
3. Press the `Create Assignment` blue button that appears in the bottom of the page
4. Fill in the form - below each field there is a short description of its purpose

# How to create tests

Public tests should be defined in a file whose name should be prefixed with the `TestTeacher`. For example, the tests 
for a class `Person` should be defined in a file called `TestTeacherPerson`.

Hidden tests should be defined in a file whose name should be prefixed with `TestTeacherHidden`. For example, `TestTeacherHiddenPerson`.

It is possible to have multiple test public and hidden test classes.
