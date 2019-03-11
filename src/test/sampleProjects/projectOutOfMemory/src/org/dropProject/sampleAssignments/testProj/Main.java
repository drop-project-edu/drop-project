package org.dropProject.sampleAssignments.testProj;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {

    static int funcaoParaTestar() {
        return 3;
    }

    static int funcaoLentaParaTestar() {
        // let's fill the memory to provoke an out of memory
        System.out.println("Starting");

        Random random = new Random();

        List<Long> lista = new ArrayList<>();

        for (long i = 0; i < 5000000; i++) {
            lista.add(random.nextLong());

            if (i % 500000 == 0) {
                System.out.println("Produced " + (i / 1000) + "K longs");
            }
        }

        System.out.println("Finished");

        return 3;
    }

    public static void main(String[] args) {
        System.out.println("Sample!");
    }
}
