This page presents some ideas to consider when creating the tests for your assignments.

##### JUnit 101
DP uses JUnit for testing. JUnit provides some multiple ready to use assertion functions, such as:

- assertEquals()
- assertTrue()
- assertFalse
- assertNotNull()

In some scenarios, the teacher might have specific comparison needs that not ensure by one of JUnit's assertions. 
If this is the case, the teacher can use `fail()` to force the test to fail on a certain condition.

##### Level of detail in feedback messages

With the JUnit API, teachers have full control of the level of detail that is given by each assert.

In this first example, the student will only receive a very generic message if the respective code fails the test (i.e. `java.lang.AssertionError`):

    @Test
    public void test001() {
        assertTrue(Main.isOdd(10));
    }

A teacher can give further information by using the alternative version of `assertTrue()` that receives a String as the first argument:

    @Test(timeout=1000)
    public void test001() {
        assertTrue("isOdd() returned false incorrectly", Main.isOdd(11));
        assertFalse("isOdd() returned true incorrectly", Main.isOdd(20));
    }

##### Use `Timeouts` to prevent infinite loops or disallow inefficient solutions

In the following example, the test function will only be allowed 1000 milliseconds (i.e. 1 second) to run:

    @Test(timeout=1000)
    public void test001() { 
        ...
    }

##### Implement tests that use input/output matching

If you prefer to create your evaluations using input/out matching instead of unit testing, you can include the 
following tool in your assignment's tests:

https://github.com/drop-project-edu/stdin-stdout-junit-helper

