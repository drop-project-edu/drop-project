package org.dropProject.sampleAssignments.testProj;

public class Main {

    static int funcaoParaTestar() {
        return 3;
    }

    static int funcaoLentaParaTestar() {
        try {
            Thread.sleep(5000);
        } catch (Exception ignore) {
            // ignore
        }
        return 3;
    }

    public static void main(String[] args) {
        System.out.println("Sample!");
    }
}
