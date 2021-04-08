This page presents some ideas to consider when creating the tests for your assignments.

##### JUnit 101
DP uses JUnit for testing. JUnit provides some multiple ready to use assertion functions, such as:

- assertEquals()
- assertTrue()
- assertFalse()
- assertNotNull()

In some scenarios, the teacher might have specific comparison needs that not ensure by one of JUnit's assertions. 
If this is the case, the teacher can use `fail()` to force the test to fail on a certain condition.

#### Ensuring test execution order

For pedagogical (or other) reasons, it might be interesting to ensure that the tests are executed in a certain order. 
This might help the student focus on more elementary issues before delving in to more complex problems.

In JUnit this can be achieved by:
1. Importing the `org.junit.FixMethodOrder` and `org.junit.runners.MethodSorters` packages;
2. Using the @FixMethodOrder(MethodSorters.NAME_ASCENDING) annotation before the declaration of the Test class.

    import org.junit.FixMethodOrder;
    import org.junit.runners.MethodSorters;

    @FixMethodOrder(MethodSorters.NAME_ASCENDING)
    public class TestPerson {
        ...
    }

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

##### Prevent hardcoded solutions (aka Overfitting)

Some students might try to cheat the system by implementing hardcoded solutions that do not actually solve the general 
problem presented in the assignment, but work for some inputs (namely, the ones used on the assignment). To mitigate this
 situation, we suggest that teachers employ the following test design strategies:
 - Multiple assertions
 - Hide test case information: function arguments
 - Hide test case information: function results

###### Multiple assertions

Test functions should include multiple assertions, covering the range of valid return function values.

For example, this should be avoided because a student's solution that always returns `true` will pass the test:

    public void test001() { 
        assertTrue(Main.isOdd(11));
    }

Instead, this alternative is preferable:

    public void test001() { 
        assertTrue(Main.isOdd(11));
        assertFalse(Main.isOdd(20));
    }

##### Hide test case information: function arguments

Teachers should try to find a balance between exposing too much and loo little information.

For example, the following test gives the student too much information about what is going on:

    @Test
    public void test001() { 
        assertTrue("isOdd(11) returned the wrong value", Main.isOdd(11));
        assertTrue("isOdd(20) return the wrong value", Main.isOdd(20));
    }

A safer alternative would be to give more generic information:

    @Test
    public void test001() {
        assertTrue("isOdd() returned the wrong value for an odd number larger than 10", Main.isOdd(11));
        assertTrue("isOdd() return the wrong value for an even number larger than 10", Main.isOdd(20));
    }

The teacher can also mix asserts that have a lot of information with asserts that have little information, 
as displayed in the following example:

    @Test
    public void test001() { 
        assertTrue("isOdd(11) returned the wrong value", Main.isOdd(11));
        assertTrue("isOdd(20) return the wrong value", Main.isOdd(20));
        
        assertTrue("isOdd() returned the wrong value, Main.isOdd(22));
    }

##### Hide test case information: function results

Although JUnit's assertion mechanism is very helpful when defining tests, sometimes it might expose too much 
information. For example, the following test will let the student know the expected contents of the function's 
output list:

    @Test
    public void test001() {
        List<Integer> expectedNumbers = new ArrayList<>();
        expectedNumbers.add(1);
        expectedNumbers.add(100);
        
        List<Integer> result = Main.getNumbers();
        
        assertEquals("getNumbers() returned an incorrect value", expectedNumbers, result);
    }

In this case, it might be preferable to "manually" perform the comparison:

    @Test
    public void test001() {
        List<Integer> expectedNumbers = new ArrayList<>();
        expectedNumbers.add(1);
        expectedNumbers.add(100);
                
        List<Integer> result = Main.getNumbers();
        
        if(result.get(0) != 1 || result.get(1) != 100) {
            fail("getNumbers() returned an incorrect value");
        }        
    }

Another alternative would be to perform other assertions before exposing the expected values. An example would be to test
the size of the returned input, before giving more detail:

    @Test
    public void test001() {
        List<Integer> expectedNumbers = new ArrayList<>();
        expectedNumbers.add(1);
        expectedNumbers.add(100);

        List<Integer> result = Main.getNumbers();

        if(result.size() != 2) {
            fail("getNumbers() returned a list with the wrong number of elements.");
        }
        
        assertEquals("getNumbers() returned in incorrect value.", expectedNumbers, result);
    }

##### Implement tests that use input/output matching

If you prefer to create your evaluations using input/out matching instead of unit testing, you can include the 
following tool in your assignment's tests:

[https://github.com/drop-project-edu/stdin-stdout-junit-helper](https://github.com/drop-project-edu/stdin-stdout-junit-helper)
