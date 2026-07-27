package org.dropproject.samples.samplekotlinassignment;

/**
 * A java file that the student left in the package of a Kotlin assignment. Since it uses the Kotlin code, javac
 * (which maven runs before the Kotlin compiler) is not able to compile it.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println(MainKt.findMax(new Integer[] { 1, 2, 3 }));
    }
}