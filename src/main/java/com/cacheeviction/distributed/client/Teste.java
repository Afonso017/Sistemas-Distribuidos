package com.cacheeviction.distributed.client;

public class Teste {
    public static void main(String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd");
            pb.inheritIO();
            pb.command("chcp", "1252").start();
            pb.start();
        } catch (Exception e) {
            System.err.println("\n" + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
