package org.dropProject.sampleAssignments.testProj;

import java.io.File;

public class Main {

    static int funcaoParaTestar() {
        // let's try to access the root of the filesystem
        new File("/").list();

        return 3;
    }

    static int funcaoLentaParaTestar() {
        System.exit(1);  // this shouldn't be allowed
        return 3;
    }

    static int funcaoQueRebenta() {
        return 3;
    }

    public static void main(String[] args) {
        System.out.println("Sample!");
    }
}
