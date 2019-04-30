package org.dropProject.samples.sampleJavaAssignment;

public class Main {

    static int findMax(int[] numbers) {
        if (numbers == null) {
            throw new IllegalArgumentException("numbers cannot be null");
        }

        int max = Integer.MIN_VALUE;
        for (int number : numbers) {
            if (number > max) {
                max = number;
            }
        }

        return max;
    }

    public static void main(String[] args) {
        int[] numbers = { 1, 3, 7, 4, 2 };
        System.out.println("max = " + findMax(numbers));
    }
}
