package org.dropProject.sampleAssignments.testProj;

public class Main {

    static int funcaoParaTestar() {
        return 3;
    }

    static int funcaoQueRebenta() {
        return 3;
    }

    static int funcaoLentaParaTestar() {
        try {
            Thread.sleep(500);
        } catch (Exception ignore) {
            // ignore
        }
        return 3;
    }

    static int funcaoParaEuTestar() {
        System.out.println("aqui");
        System.out.println("--->" + System.getProperty("dropProject.currentUserId"));
        return 4;
    }

    public static void main(String[] args) {
        System.out.println("Sample!");
    }
}
