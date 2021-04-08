## Assignments and Submissions

The best way to understand how Drop Project (DP) works is to start with its two main concepts: Assignments and Submissions. Assignments are created by teachers and Submissions are created by students, according to the assignment instructions. The students' goal is to pass all the tests.

### Assignments

The interesting thing is that assignments are just regular maven projects (in Java or Kotlin), stored in a regular git repository - there's nothing specific to DP in them.

    + assignment
    |--- pom.xml     (includes the checkstyle and junit plugins)
    |--- checkstyle.xml     (the rules to validate the code style, e.g., variables must start with lowercase)
    |--- instructions.html     (optional, the assignment instructions)
    |--- src
    |------ main
    |--------- ...      (here goes the reference implementation, in maven structure)
    |------ test
    |--------- ...      (junit tests that will validate students' submissions)

Users with the TEACHER role will be able to create an assignment by connecting DP with its repository and filling some information. You can see an example in https://github.com/palves-ulht/sampleJavaAssignment

The good thing is that you can code and test the assignments in your IDE, without using DP. After you finished, just connect DP to your repository. If you make further changes, just refresh the assignment in DP.

### Submissions

After the assignment is marked active, students will be able to submit their projects, trying to pass all the assignment junit tests. They can submit their project by dropping a zip file or by connecting DP to a git repository containing their submission (this is specified by the teacher when she creates the assignment).

Students can submit alone or in group - they just have to include an AUTHORS.txt in their project. One of the things that students like in DP is the fact that they can submit multiple times until they pass all the tests. It's up to the teacher to determine if the number of submissions has an effect in the final grade.

The structure is the standard structure for standalone Java/Kotlin projects:

    + submission
    |--- AUTHORS.txt     (includes the members of the group)
    |--- src
    |--------- ...      (here goes the student source files)
