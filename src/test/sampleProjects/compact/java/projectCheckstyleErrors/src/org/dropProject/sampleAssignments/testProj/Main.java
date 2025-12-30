package org.dropProject.sampleAssignments.testProj;

class aluno {
    int Numero;
    int mVotos;
}

public class Main {

    static final int constante = 30;

    static void FazCoisas() {
        int x;
    }

    static int funcaoParaTestar() {
        return 3;
    }

    static int funcaoQueRebenta() {
        return 3;
    }

    static int funcaoLentaParaTestar() {
        return 3;
    }

    public static void main(String[] args) {
        System.out.println("Sample!");
        if (args.length > 1) System.out.println("aqui");
        System.exit(2);  // this instruction is not allowed by checkstyle
    }
}
