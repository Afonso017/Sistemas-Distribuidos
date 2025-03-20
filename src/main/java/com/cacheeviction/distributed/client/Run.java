package com.cacheeviction.distributed.client;

import com.cacheeviction.distributed.node0.LocationServer;
import com.cacheeviction.distributed.node1.ProxyServer;
import com.cacheeviction.distributed.node2.ApplicationServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Run {
    private static List<Process> serverProcesses = new ArrayList<>();

    public static void main(String[] args) {
        Class<?>[] servers = {LocationServer.class, ApplicationServer.class, ProxyServer.class};
        runServersMain(servers);

        Scanner reader = new Scanner(System.in);
        Process process = null;

        while (true) {
            switch (reader.nextLine()) {
                case "client" -> {
                    try {
                        ProcessBuilder pb = getProcessBuilder(System.getProperty("java.class.path"));
                        process = pb.inheritIO().start();
                    } catch (Exception e) {
                        System.err.println("\n" + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }

                case "app" -> {
                    try {
                        ProcessBuilder pb = getServerProcessBuilder(ApplicationServer.class);
                        process = pb.start();
                        serverProcesses.add(process);
                    } catch (Exception e) {
                        System.err.println("\n" + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }

                case "proxy" -> {
                    try {
                        ProcessBuilder pb = getServerProcessBuilder(ProxyServer.class);
                        process = pb.start();
                        serverProcesses.add(process);
                    } catch (Exception e) {
                        System.err.println("\n" + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }

                default -> {
                    System.out.println("Encerrando servidores...");
                    for (Process serverProcess : serverProcesses) {
                        serverProcess.destroy();
                    }
                    if (process != null) {
                        process.destroy();
                    }
                    System.exit(0);
                }
            }
        }
    }

    private static void runServersMain(Class<?>[] clazz) {
        for (Class<?> c : clazz) {
            try {
                ProcessBuilder pb = getServerProcessBuilder(c);
                Process serverProcess = pb.start();
                serverProcesses.add(serverProcess);
                System.out.println("Servidor " + c.getSimpleName() + " iniciado com PID: " + serverProcess.pid());
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static ProcessBuilder getProcessBuilder(String classpath) {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath,
            "-p", "\"C:\\Users\\afons\\.m2\\repository\\com\\google\\code\\gson\\gson\\2.10.1\\gson-2.10.1.jar;C:\\Users\\afons\\OneDrive\\Área de Trabalho\\Sistemas Distribuídos\\DistributedCacheEviction-Uni3\\target\\classes;C:\\Users\\afons\\.m2\\repository\\org\\openjfx\\javafx-base\\22\\javafx-base-22-win.jar;C:\\Users\\afons\\.m2\\repository\\org\\openjfx\\javafx-controls\\22\\javafx-controls-22-win.jar;C:\\Users\\afons\\.m2\\repository\\org\\openjfx\\javafx-fxml\\22\\javafx-fxml-22-win.jar;C:\\Users\\afons\\.m2\\repository\\org\\openjfx\\javafx-graphics\\22\\javafx-graphics-22-win.jar\"",
            "-m", "com.cacheeviction.distributed/com.cacheeviction.distributed.client.Client",
            "localhost:3000/LocationServer");
        return pb;
    }

    private static ProcessBuilder getServerProcessBuilder(Class<?> serverClass) {
        String serverName = serverClass.getSimpleName();
        String serverMainClass = serverClass.getName();

        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath + serverName, "localhost:3000/LocationServer");
        return pb;
    }
}
