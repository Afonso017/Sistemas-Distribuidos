package com.cacheeviction.distributed.global.server;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private final String path;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public Logger(String path, boolean erase) {
        this.path = path;

        // se erase for true, limpa o arquivo
        // caso contrário adiciona um \n antes se o arquivo não estiver vazio
        try (var reader = new java.io.BufferedReader(new java.io.FileReader(path))) {
            if (reader.readLine() != null) {
                try (var writer = new FileWriter(path, !erase)) {
                    if (!erase) writer.append("\n"); else writer.write("");
                } catch (IOException e) {
                    System.err.println("Erro ao escrever no Log " + path);
                }
            }
        } catch (IOException ignored) {}
    }

    public synchronized void append(String text) {
        String dateTime = LocalDateTime.now().format(formatter);
        String logEntry = dateTime + " " + text;
        try (var writer = new FileWriter(path, true)) {
            writer.append(logEntry).append("\n");
        } catch (IOException e) {
            System.err.println("Erro ao escrever no Log " + path);
        }
    }

    // lê o arquivo de log e retorna o conteúdo em string
    public String toString() {
        try (var reader = new java.io.BufferedReader(new java.io.FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            return sb.toString();
        } catch (IOException e) {
            System.err.println("Erro ao ler o Log " + path);
            return "";
        }
    }
}
