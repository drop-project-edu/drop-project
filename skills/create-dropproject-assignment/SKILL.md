---
name: create-dropproject-assignment
description: Create a Drop Project programming assignment end to end - author the teacher's Maven project, push it to a git repository, register the assignment over Drop Project's MCP server, install the deploy key, and iterate on the validation report until the assignment can be activated. Use when asked to create, set up, or publish a Drop Project assignment, or when a Drop Project validation report has to be fixed.
---

# Creating a Drop Project assignment

Drop Project is a platform where students submit code that is automatically compiled, tested and
graded against unit tests written by the teacher.

The single most important thing to understand: **an assignment is not defined inside Drop
Project**. It is defined by a git repository that the teacher owns, containing a Maven project with
the teacher's unit tests. Drop Project keeps a read-only clone of that repository. Creating an
assignment therefore spans two systems, and Drop Project only owns half of it.

| Step | Where it happens |
|---|---|
| Author the Maven project | your filesystem |
| Create the repository and push | the git host (`gh`, `git`) |
| Register the assignment | Drop Project, `create_assignment` |
| Install the deploy key | the git host (`gh`, or its web ui) |
| Clone and validate | Drop Project, `connect_assignment` |
| Fix, push, revalidate | both, `refresh_assignment` |
| Let students submit | Drop Project, `set_assignment_active` |

Drop Project never creates repositories, never pushes to them, and never installs deploy keys. If
the Drop Project MCP server is not configured, do everything up to "push" and hand the teacher the
remaining steps.

## Before you start

Ask for whatever is missing - do not invent them:

- what the exercise is, and which functions or classes the students must write
- the assignment id (letters, numbers, hyphens and underscores only) and its human readable name
- the git repository to use, or permission to create one
- the language (Java or Kotlin) and the package name

## 1. Author the teacher's project

Copy `templates/java/` as the starting point. It is a Maven project that passes Drop Project's
validator, so start from it rather than writing a `pom.xml` from scratch - most validation errors
come from a `pom.xml` that is missing one of the Drop Project specific bits.

```
pom.xml                         see templates/java/pom.xml
checkstyle.xml                  style rules applied to the students' code
instructions.md                 what the students see on the assignment page
src/main/java/<pkg>/...         the skeleton the students start from, if any
src/test/java/<pkg>/TestTeacher<Name>.java          the tests that grade them
src/test/java/<pkg>/TestTeacherHidden<Name>.java    optional, tests they can't see
```

Rules the validator enforces, worth getting right the first time:

- **Test class names.** Teacher tests must live in `src/test` in classes whose name starts with
  `TestTeacher`. Hidden tests, which students never see the source of, start with
  `TestTeacherHidden`. If the assignment also accepts student tests, *every* teacher class must
  use the `TestTeacher` prefix.
- **Every test method needs a timeout**, e.g. `@Test(timeout = 500)`. A student's infinite loop
  otherwise blocks the evaluation queue. The template uses JUnit 4; JUnit 5 works too, with
  `@Test` plus `@Timeout(1)`.
- **Order the tests** by naming them `test_001_...`, `test_002_...`. Students read the report top
  to bottom, so a deliberate order tells them where to start.
- **If you add hidden tests**, you must also pass `hiddenTestsVisibility` when creating the
  assignment, or validation fails with an error. `SHOW_PROGRESS` (students see how many hidden
  tests passed) is the usual choice.
- **`instructions.md`** is rendered on the assignment page. Write it for the students: what to
  implement, the expected signatures, and what is graded.

Write the tests against the exercise, then verify locally before pushing:

```bash
mvn -q test -Ddp.argLine=
```

`dp.argLine` is a property Drop Project sets at evaluation time; passing it empty keeps the
`pom.xml` runnable outside Drop Project.

## 2. Create the repository and push

The repository can be private - Drop Project reads it with a deploy key, not with the teacher's
credentials.

```bash
git init && git add . && git commit -m "Initial assignment"
gh repo create <owner>/<repo> --private --source=. --push
```

Note the **SSH** url, `git@github.com:<owner>/<repo>.git`. Drop Project rejects https urls.

## 3. Register the assignment

Call the `create_assignment` MCP tool. Required: `assignmentId`, `assignmentName`,
`gitRepositoryUrl`. Everything else is optional; the ones that matter most often:

| Argument | Use it for |
|---|---|
| `packageName` | lets Drop Project trim stacktraces shown to students - always set it |
| `language` | `JAVA` (default) or `KOTLIN` |
| `dueDate` | ISO-8601, e.g. `2026-10-15T23:59` |
| `hiddenTestsVisibility` | required if you wrote `TestTeacherHidden*` classes |
| `acceptsStudentTests`, `minStudentTests` | when students must write their own tests |
| `minGroupSize`, `maxGroupSize` | group assignments |
| `visibility`, `assignees` | `PRIVATE` restricts submission to the listed user ids |
| `acl` | other teachers who may change the assignment |
| `leaderboardType` | opts the assignment into a leaderboard |

The tool returns an **ssh public key**. The assignment exists at this point but is inactive and
disconnected.

## 4. Install the deploy key

Drop Project only reads the repository, so a read-only deploy key is enough and is what you should
use. Write the returned key to a file and install it:

```bash
gh repo deploy-key add <key file> --repo <owner>/<repo> --title "Drop Project"
```

Without `gh`, paste it at `https://github.com/<owner>/<repo>/settings/keys`, leaving write access
unchecked.

## 5. Connect and fix the validation report

Call `connect_assignment` with the assignment id. Drop Project clones the repository and validates
what it finds. If the clone fails, the deploy key is almost always the reason - the tool returns
the public key again so you can retry step 4, then call `connect_assignment` again.

The validation report comes back with the response. Then loop until it has no errors:

1. read the report
2. fix the assignment's files
3. commit and push
4. call `refresh_assignment` to pull and revalidate

`INFO` entries are confirmations, not problems. `WARNING` entries are worth fixing but don't block
anything. Only `ERROR` prevents activation.

Common report entries and what they mean:

| Report says | Fix |
|---|---|
| Assignment must have a pom.xml | the repository root isn't the Maven project root |
| POM file is not prepared to calculate coverage | add the jacoco plugin, or drop `calculateStudentTestsCoverage` |
| You are using an outdated version of checkstyle | the `checkstyle` dependency of the checkstyle plugin must be 9.0.1 or newer |
| You have hidden tests but you didn't set their visibility | set `hiddenTestsVisibility` on the assignment |
| You must have at least one test class on src/test/** whose name starts with Test | the test class prefix is wrong |
| You haven't defined a timeout for N test methods | add `timeout` to every `@Test` |
| Assignment without package | pass `packageName` |

## 6. Activate

`set_assignment_active` with `active: true`. This is refused while the report has errors or while
the assignment has never been connected, which is the intended safety net: students would
otherwise submit to an assignment Drop Project can't evaluate.

Confirm with `get_assignment_info`, and give the teacher the assignment's url.

## Notes

- **Never** ask for, or handle, the private key. It is generated inside Drop Project and stays
  there.
- Changing an assignment's git repository url after creation is not supported. If the wrong url
  was used, the assignment has to be recreated.
- Editing the teacher's files later always means push then `refresh_assignment`. Drop Project does
  not poll the repository.
- Submissions that were already evaluated keep the grades they were given under the previous
  configuration; they are not re-evaluated automatically.
