package com.cacheeviction.distributed.node2;

import com.cacheeviction.distributed.global.server.Logger;
import com.cacheeviction.distributed.global.util.Auth;
import com.cacheeviction.distributed.global.util.JSON;
import com.cacheeviction.distributed.global.util.ServiceOrder;
import com.cacheeviction.distributed.node0.LocationServerInterface;
import com.google.gson.reflect.TypeToken;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.FileWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationServer extends UnicastRemoteObject implements ApplicationServerInterface {
    private final ConcurrentHashMap<Integer, ServiceOrder> database;
    private final List<Token> users;
    private final String locationUrl;
    private String location;
    private List<String> backups;
    private Logger logger;
    private String url;
    private int port;
    private boolean isBackup;

    private record Token(Auth auth, boolean role) {
        public Token {
            if (auth == null) {
                throw new IllegalArgumentException("Auth cannot be null");
            }
        }
        public Auth getAuth() {
            return auth;
        }

        public boolean getRole() {
            return role;
        }
    }

    public ApplicationServer(String locationUrl) throws RemoteException {
        this.locationUrl = locationUrl;
        users = List.of(
            new Token(new Auth("user", "user1", "pass123"), false),
            new Token(new Auth("admin", "admin", "admin123"), true)
        );
        database = new ConcurrentHashMap<>();
        backups = new ArrayList<>();
        port = 8080;
        isBackup = false;
        start();
    }

    private void start() {
        // inicializa servidor
        while (logger == null) {
            try {
                String localhost = InetAddress.getLocalHost().getHostAddress();
                url = localhost + ":" + port + "/ApplicationServer";

                LocateRegistry.createRegistry(port);
                Naming.rebind("rmi://" + url, this);

                location = "_" + localhost + "_" + port;
                logger = new Logger("applicationServerLog" + location + ".txt", false);
                log("Application Server started on " + url);
            } catch (MalformedURLException | RemoteException | UnknownHostException e) {
                System.err.println("Error starting application server: " + e.getMessage()
                    + "Retrying on port " + ++port);
            }
        }

        // envia endereço para servidor de localização
        try {
            LocationServerInterface lsi = (LocationServerInterface)
                Naming.lookup("rmi://" + locationUrl);

            log("Sent address to location server: " + locationUrl);
            lsi.addApplication(JSON.create(url).toJson());
        } catch (Exception e) {
            log("Failed to connect to location server: " + e.getMessage());
            System.exit(1);
        }

        if (!isBackup) {
            // carrega banco de dados, caso arquivo exista e não esteja vazio
            loadDatabaseFromFile();
        }

        // cria thread para persistir o banco a cada 30 segundos
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000);
                    persistDatabase();
                } catch (InterruptedException e) {
                    log("Failed to persist database: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public String request(String message) throws RemoteException {
        String clientHost = "";
        try {
            clientHost = RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            log("Error getting client host: " + e.getMessage());
        }

        JSON request = JSON.fromJson(message);
        Auth clientAuth = request.getAuth();
        String clientName = clientAuth.getUsername();

        switch (request.getOperation()) {
            case "auth" -> {
                log(clientName + ":" + clientHost + " -> auth");
                Token user = authenticated(clientAuth);
                if (user != null) {
                    log(clientName + ":" + clientHost + " <- authenticated: " + (user.getRole() ? "admin" : "user"));
                    clientAuth.setRole(user.getRole() ? "admin" : "user");
                    return JSON.create("auth", "authenticated", clientAuth).toJson();
                }
                log(clientName + ":" + clientHost + " <- unauthorized: invalid credentials");
                return JSON.create("auth", "unauthorized: invalid credentials", null).toJson();
            }

            case "register" -> {
                new Thread(() -> forwardToBackups(message)).start();

                ServiceOrder so = request.getMessageAs(ServiceOrder.class);
                log(clientName + ":" + clientHost + " -> register: " + so);

                // if the user is authenticated
                Token user = authenticated(clientAuth);
                if (user != null) {
                    // if the order already exists
                    if (database.containsKey(so.getCode())) {
                        log(clientName + ":" + clientHost + " <- register: already exists");
                        return JSON.create("register", "already exists", null).toJson();
                    }
                    database.put(so.getCode(), so);
                    log(clientName + ":" + clientHost + " <- register: success");
                    return JSON.create("register", "success", null).toJson();
                }
                log(clientName + ":" + clientHost + " <- unauthorized: invalid credentials");
                return JSON.create("register", "unauthorized: invalid credentials", null).toJson();
            }

            case "search" -> {
                new Thread(() -> forwardToBackups(message)).start();

                int key = request.getMessageAs(Integer.class);
                log(clientName + ":" + clientHost + " -> search: " + key);

                // if the user is authenticated
                Token user = authenticated(request.getAuth());
                if (user != null) {
                    ServiceOrder so = new ServiceOrder(key, null, null);
                    ServiceOrder found = database.get(key);
                    log(clientName + ":" + clientHost + " <- search: " + Objects.requireNonNullElse(found, so));
                    return JSON.create("search", Objects.requireNonNullElse(found, so), null).toJson();
                }
                log(clientName + ":" + clientHost + " <- unauthorized: invalid credentials");
                return JSON.create("search", "unauthorized: invalid credentials", null).toJson();
            }

            case "edit" -> {
                new Thread(() -> forwardToBackups(message)).start();

                ServiceOrder so = request.getMessageAs(ServiceOrder.class);
                log(clientName + ":" + clientHost + " -> edit: " + so);

                // if the user is authenticated and authorized
                Token user = authenticated(request.getAuth());
                if (user != null && user.getRole()) {
                    if (database.containsKey(so.getCode())) {
                        database.put(so.getCode(), so);
                        log(clientName + ":" + clientHost + " <- edit: success");
                        return JSON.create("edit", "success", null).toJson();
                    }
                    log(clientName + ":" + clientHost + " <- edit: not found");
                    return JSON.create("edit", "not found", null).toJson();
                }
                log(clientName + ":" + clientHost + " <- unauthorized: invalid credentials");
                return JSON.create("edit", "unauthorized: invalid credentials", null).toJson();
            }

            case "remove" -> {
                new Thread(() -> forwardToBackups(message)).start();

                ServiceOrder so = request.getMessageAs(ServiceOrder.class);
                log(clientName + ":" + clientHost + " -> remove: " + so);

                // if the user is authenticated and authorized
                Token user = authenticated(request.getAuth());
                if (user != null && user.getRole()) {
                    database.remove(so.getCode());
                    log(clientName + ":" + clientHost + " <- remove: success");
                    return JSON.create("remove", "success", null).toJson();
                }
                log(clientName + ":" + clientHost + " <- unauthorized: invalid credentials");
                return JSON.create("remove", "unauthorized: invalid credentials", null).toJson();
            }
        }

        return JSON.create("error", "invalid operation", null).toJson();
    }

    @Override
    public String getUsers() throws RemoteException {
        return JSON.create(users).toJson();
    }

    @Override
    public String getSize() throws RemoteException {
        return JSON.create(database.size()).toJson();
    }

    @Override
    public String getDatabaseArrayListString() throws RemoteException {
        return JSON.create(toArrayListString()).toJson();
    }

    @Override
    public String getDatabaseObservableList() throws RemoteException {
        return JSON.create(toObservableList()).toJson();
    }

    @Override
    public String readLog() throws RemoteException {
        return JSON.create(logger.toString()).toJson();
    }

    @Override
    public void backupRequest(String message) throws RemoteException {
        JSON request = JSON.fromJson(message);

        switch (request.getOperation()) {
            case "register" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class);

                if (!database.containsKey(so.getCode())) {
                    log("Backup -> register: " + so);
                    database.put(so.getCode(), so);
                } else {
                    log("Backup -> register: " + so + " already exists");
                }
            }

            case "edit" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class);

                if (database.containsKey(so.getCode())) {
                    log("Backup -> edit: " + so);
                    database.put(so.getCode(), so);
                } else {
                    log("Backup -> edit: " + so + " not found");
                }
            }

            case "remove" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class);

                if (database.containsKey(so.getCode())) {
                    log("Backup -> remove: " + so);
                    database.remove(so.getCode());
                } else {
                    log("Backup -> remove: " + so + " not found");
                }
            }
        }
    }

    @Override
    public void setBackups(String json) throws RemoteException {
        synchronized (this) {
            backups = JSON.fromJson(json).getMessageAs(new TypeToken<ArrayList<String>>() {}.getType());
            log("Backups received from location server: " + backups);
        }
    }

    @Override
    public String getDatabase() throws RemoteException {
        return JSON.create(database).toJson();
    }

    @Override
    public void setDatabase(String database) throws RemoteException {
        isBackup = true;
        synchronized (this) {
            this.database.putAll(JSON.fromJson(database)
                .getMessageAs(new TypeToken<ConcurrentHashMap<Integer, ServiceOrder>>() {}.getType()));
            log("Received database from location server");
        }
    }

    private void forwardToBackups(String request) {
        for (String backup : backups) {
            try {
                ApplicationServerInterface asi = (ApplicationServerInterface)
                    Naming.lookup("rmi://" + backup);
                asi.backupRequest(request);
            } catch (Exception e) {
                log("Failed to connect to backup server: " + e.getMessage());
                synchronized (this) {
                    backups.remove(backup);
                }
            }
        }
    }

    private void persistDatabase() {
        String filename = "backup" + location + ".txt";
        try (FileWriter fileWriter = new FileWriter(filename)) {
            fileWriter.write(JSON.toPrettyJson(database));
        } catch (Exception e) {
            log("Failed to persist database: " + e.getMessage());
        }
    }

    private void loadDatabaseFromFile() {
        String filename = "backup" + location + ".txt";
        try (Scanner scanner = new Scanner(new java.io.File(filename))) {
            if (scanner.hasNextLine()) {
                StringBuilder json = new StringBuilder();
                while (scanner.hasNextLine()) {
                    json.append(scanner.nextLine());
                }
                database.putAll(JSON.fromPrettyJson(json.toString(),
                    new TypeToken<ConcurrentHashMap<Integer, ServiceOrder>>() {}.getType()));
                log("Database loaded from " + filename);
            }
        } catch (Exception e) {
            log("Failed to load database: " + e.getMessage());
        }
    }

    private ArrayList<ServiceOrder> toArrayList() {
        return new ArrayList<>(database.values());
    }

    private ArrayList<String> toArrayListString() {
        ArrayList<String> list = new ArrayList<>();
        for (ServiceOrder so : database.values()) {
            list.add(so.toString());
        }
        return list;
    }

    private ObservableList<ServiceOrder> toObservableList() {
        return FXCollections.observableArrayList(toArrayList());
    }

    private Token authenticated(Auth userAuth) {
        // check if the user has the correct credentials in database
        if (userAuth != null) {
            return users.stream()
                .filter(u -> u.getAuth().equals(userAuth))
                .findFirst().orElse(null);
        }
        return null;
    }

    private void log(String s) {
        System.out.println(s);
        logger.append(s);
    }

    public static void main(String[] args) {
        try {
            Scanner reader = new Scanner(System.in);
            System.out.print("Digite a url do servidor de localização (ip:porta/serviço): ");
            new ApplicationServer(reader.nextLine());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
