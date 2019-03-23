package org.dropProject.sampleAssignments.testProj;

public class Main {

    static int funcaoParaTestar() {
        System.out.println("******************************");
        System.out.println("******************************");
        System.out.println("REFERENCE IMPLEMENTATION! THIS SHOULDN'T SHOW UP DURING A STUDENT SUBMISSION!");
        System.out.println("******************************");
        System.out.println("******************************");

        // for automatic tests with currentUserId
        System.out.println(">>>> currentUserId = [" + System.getProperty("dropProject.currentUserId") + "]");

        return 3;
    }

    static int funcaoLentaParaTestar() {
        return 3;
    }
}
