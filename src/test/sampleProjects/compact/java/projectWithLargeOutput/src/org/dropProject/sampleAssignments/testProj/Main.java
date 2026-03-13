package org.dropProject.sampleAssignments.testProj;

public class Main {

    static int funcaoParaTestar() {
        // this fuction will produce too much output and DP should mark it with a fatal error
        for (int i = 0; i < 3000; i++) {
            System.out.println("Output" + i);
        }
        return 3;
    }

    static int funcaoQueRebenta() {
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
