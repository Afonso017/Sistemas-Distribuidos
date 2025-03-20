package com.cacheeviction.distributed.node1;

import com.cacheeviction.distributed.global.server.Cache;
import com.cacheeviction.distributed.global.server.Logger;
import com.cacheeviction.distributed.global.util.Auth;
import com.cacheeviction.distributed.global.util.JSON;
import com.cacheeviction.distributed.global.util.ServiceOrder;
import com.cacheeviction.distributed.node0.LocationServerInterface;
import com.cacheeviction.distributed.node2.ApplicationServerInterface;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class ProxyServer extends UnicastRemoteObject implements ProxyServerInterface {
    private final String locationUrl;
    private Cache<ServiceOrder> cache;
    private Logger logger;
    private ApplicationServerInterface app;
    private LocationServerInterface loc;
    private List<String> proxies;
    private List<Token> users;
    private String appUrl;
    private String url;
    private int port;

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

    @FunctionalInterface
    interface RetryableOperation {
        String execute() throws RemoteException, InvocationTargetException, IllegalAccessException;
    }

    public ProxyServer(String locationUrl) throws RemoteException {
        this.locationUrl = locationUrl;
        proxies = new ArrayList<>();
        port = 9000;
        start();
    }

    protected void start() {
        // inicializa servidor proxy
        String str;
        while (logger == null) {
            try {
                String localhost = InetAddress.getLocalHost().getHostAddress();
                url = localhost + ":" + port + "/ProxyServer";

                LocateRegistry.createRegistry(port);
                Naming.rebind("rmi://" + url, this);

                String location = "_" + localhost + "_" + port;
                logger = new Logger("proxyServerLog" + location + ".txt", false);
                cache = new Cache<>(location, 30);
                log("Proxy server started on " + url);
            } catch (RemoteException | MalformedURLException | UnknownHostException e) {
                System.err.println("Error starting proxy server: " + e.getMessage()
                    + ", retrying on port " + ++port);
            }
        }

        // comunicação inicial com o servidor de localizações
        try {
            loc = (LocationServerInterface)
                Naming.lookup("rmi://" + locationUrl);

            // envia o endereço do servidor proxy para o servidor de localizações
            JSON request = JSON.create(url);
            log("Proxy server url sent to location server: " + locationUrl);
            Objects.requireNonNull(loc).addProxy(request.toJson());

            // requisita o endereço do servidor de aplicação
            appUrl = JSON.fromJson(loc.getApplication(request.toJson())).getMessageAs(String.class);
            str = "Critical error: no application servers available";
            if (appUrl.equals(str)) {
                log(str);
                System.exit(1);
            }
        } catch (RemoteException | MalformedURLException | NotBoundException e) {
            log("Failed to connect to " + locationUrl);
            System.exit(1);
        }

        // conecta com o servidor de aplicação
        try {
            app = (ApplicationServerInterface)
                Naming.lookup("rmi://" + appUrl);

            JSON json = JSON.fromJson(app.getUsers());
            Type listType = new TypeToken<List<Token>>() {}.getType();
            users = json.getMessageAs(listType);

            log("Connected to application server at " + appUrl);
        } catch (RemoteException | MalformedURLException | NotBoundException e) {
            log("Critical error: no application servers available");
            System.exit(1);
        }
    }

    @Override
    public void setProxies(String address) throws RemoteException {
        synchronized (this) {
            proxies = JSON.fromJson(address).getMessageAs(new TypeToken<List<String>>() {}.getType());
        }
        log("Received proxy server addresses from location server: " + proxies);
    }

    @Override
    public String request(String message) throws RemoteException {
        String clientHost;
        try {
            clientHost = RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            log("Failed to get client host: " + e.getMessage());
            return null;
        }

        JSON request = JSON.fromJson(message);
        Auth clientAuth = request.getAuth();
        String clientName = clientAuth.getUsername();
        String clientRole = clientAuth.getRole();

        switch (request.getOperation()) {
            case "auth" -> {
                // autentica o usuário
                log(clientName + ":" + clientHost + " -> auth");
                Token user = authenticated(clientAuth);
                if (user != null) {
                    clientAuth.setRole(user.getRole() ? "admin" : "user");
                    log(clientName + " <- authenticated as " + clientAuth.getRole());
                    return JSON.create("auth","authenticated", clientAuth).toJson();
                }
                log(clientName + " <- unauthorized: invalid credentials");
                return JSON.create("auth","unauthorized: invalid credentials", null).toJson();
            }

            case "search" -> {
                int key = request.getMessageAs(Integer.class);
                log(clientName + ":" + clientHost + " -> requesting data from cache: " + key);

                // busca na cache
                ServiceOrder found = cache.search(key);

                // se tiver na cache, aumenta a prioridade e retorna para o cliente
                if (found != null) {
                    synchronized (this) {
                        cache.increasePriority(found);
                    }
                    log(clientName + ":" + clientHost + " <- found in cache: " + found);
                    return JSON.create("search", found, null).toJson();
                }

                // se não tiver na cache local, busca em outras caches
                String responseCache = forwardToProxies(message);
                if (responseCache != null) return responseCache;

                // se não tiver em nenhuma cache, busca no servidor de aplicação
                String processResponse = processWithRetry(() -> app.request(message));
                if (processResponse.contains("error")) {
                    return JSON.create(processResponse).toJson();
                }
                JSON response = JSON.fromJson(processResponse);
                found = response.getMessageAs(ServiceOrder.class);

                // se encontrou no servidor de aplicação, adiciona na cache e retorna para o cliente
                if (found != null && found.getName() != null) {
                    synchronized (this) {
                        cache.add(found);
                    }
                    log(clientName + ":" + clientHost + " <- found in application: " + found);
                    return response.toJson();
                }

                // se não encontrou no servidor de aplicação, retorna null para o cliente
                return JSON.create("search", null, null).toJson();
            }

            case "edit" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class); // extrai a so da requisição
                log(clientName + ":" + clientHost + " -> edit: " + so);

                // se o usuário estiver autenticado como admin, edita a ordem de serviço
                if (Objects.equals(clientRole, "admin")) {

                    // envia request p2p de edição para cada proxy
                    forwardToProxies(message);

                    // edita se estiver na cache local
                    ServiceOrder search = cache.search(so.getCode());
                    if (search != null) {
                        synchronized (this) {
                            search.setName(so.getName());
                            search.setDescription(so.getDescription());
                            cache.add(so);
                        }
                    }

                    // request para editar no servidor de aplicação
                    try {
                        return app.request(message);
                    } catch (RemoteException e) {
                        log("Failed to connect to application server: " + e.getMessage());
                        reconnect();
                        log("Retrying to connect to " + appUrl);

                        try {
                            return app.request(message);
                        } catch (JsonSyntaxException | RemoteException ex) {
                            log("Retry failed: " + ex.getMessage());
                            System.exit(1);
                            return JSON.create("edit", "Application server is down", null).toJson();
                        }
                    }
                }
                return JSON.create("edit", "unauthorized: forbidden", null).toJson();
            }

            case "remove" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class); // extrai a so da requisição
                log(clientName + ":" + clientHost + " -> remove: " + so);

                // se o usuário estiver autenticado como admin, remove a ordem de serviço
                if (clientRole.equals("admin")) {
                    // envia request p2p de remoção para cada proxy
                    forwardToProxies(message);

                    // remove se encontrar na cache local
                    synchronized (this) {
                        cache.remove(so);
                    }

                    // request para remover do servidor de aplicação
                    JSON response;
                    try {
                        response = JSON.fromJson(app.request(message));
                    } catch (RemoteException e) {
                        log("Failed to connect to application server: " + e.getMessage());
                        reconnect();
                        log("Retrying to connect to " + appUrl);

                        try {
                            response = JSON.fromJson(app.request(message));
                        } catch (JsonSyntaxException | RemoteException ex) {
                            log("Retry failed: " + ex.getMessage());
                            System.exit(1);
                            return JSON.create("remove", "Application server is down", null).toJson();
                        }
                    }
                    return response.toJson();
                }
                return JSON.create("remove", "unauthorized: forbidden", null).toJson();
            }

            case "close" -> {
                log(clientName + ":" + clientHost + " -> close");
                loc.updateWorkload(url);
                return null;
            }

            default -> {
                // se a operação não for reconhecida, então encaminha para o servidor de aplicação
                log(clientName + ":" + clientHost + " -> forwarding to application: " + message);
                String response = forwardToApplication(message);
                log(clientName + ":" + clientHost + " <- response from application: " + response);
                return response;
            }
        }
    }

    @Override
    public String p2pRequest(String message) throws RemoteException {
        String clientHost;
        try {
            clientHost = RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            log("Failed to get client host: " + e.getMessage());
            return null;
        }

        JSON request = JSON.fromJson(message);

        switch (request.getOperation()) {
            case "search" -> {
                int key = request.getMessageAs(Integer.class);
                log(clientHost + " -> proxy requesting data from cache: " + key);

                // busca na cache
                ServiceOrder found = cache.search(key);

                // se tiver na cache, aumenta a prioridade e retorna para o proxy
                if (found != null) {
                    synchronized (this) {
                        cache.increasePriority(found);
                    }
                    log(clientHost + " <- found in cache: " + found);
                    return JSON.create("search", found, null).toJson();
                }

                // se não tiver na cache local, retorna null para o proxy
                log(clientHost + " <- not found in cache");
                return JSON.create("search", null, null).toJson();
            }

            case "edit" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class); // extrai a so da requisição
                log(clientHost + " -> proxy edit: " + so);

                // verifica se está na cache local
                ServiceOrder search = cache.search(so.getCode());
                if (search != null) {
                    synchronized (this) {
                        // atualiza a ordem de serviço na cache local
                        search.setName(so.getName());
                        search.setDescription(so.getDescription());
                        cache.add(so);
                    }
                }
            }

            case "remove" -> {
                ServiceOrder so = request.getMessageAs(ServiceOrder.class); // extrai a so da requisição
                log( clientHost + " -> proxy remove: " + so);

                // remove da cache local, se existir
                synchronized (this) {
                    cache.remove(so);
                }
            }
        }
        return null;
    }

    private String forwardToProxies(String message) {
        JSON request = JSON.fromJson(message);
        if (request.getOperation().equals("search")) {
            for (String address : proxies) {
                try {
                    ProxyServerInterface proxy = (ProxyServerInterface)
                        Naming.lookup("rmi://" + address);

                    JSON response = JSON.fromJson(proxy.p2pRequest(message));
                    String responseMessage = response.getMessageAs(String.class);

                    if (responseMessage != null) {
                        log("Element found in proxy " + address);
                        return response.toJson();
                    }
                } catch (NotBoundException | MalformedURLException | RemoteException e) {
                    log("Proxy " + address + " is down.");
                    synchronized (this) {
                        proxies.remove(address);
                    }
                }
            }
            return null;
        } else {
            for (String address : proxies) {
                try {
                    ProxyServerInterface proxy = (ProxyServerInterface)
                        Naming.lookup("rmi://" + address);

                    proxy.p2pRequest(message);
                } catch (NotBoundException | MalformedURLException | RemoteException e) {
                    log("Proxy " + address + " is down.");
                    synchronized (this) {
                        proxies.remove(address);
                    }
                }
            }
        }

        return null;
    }

    public String forwardToApplication(String message) {
        JSON request = JSON.fromJson(message);
        String operation = request.getOperation();

        // percorre os métodos da interface do servidor de aplicação
        for (Method method : ApplicationServerInterface.class.getMethods()) {
            if (method.getName().equals(operation)) {
                String result;

                if (method.getParameterCount() == 0) {
                    result = processWithRetry(() -> method.invoke(app).toString());
                } else {
                    Object param = request.getMessageAs(method.getParameterTypes()[0]);
                    result = processWithRetry(() -> method.invoke(app, param).toString());
                }

                return result;
            }
        }

        // se não encontrou, tenta a requisição genérica
        return processWithRetry(() -> app.request(message));
    }

    // processa a resposta do servidor de aplicação com uma tentativa de reconexão
    private String processWithRetry(RetryableOperation operation) {
        try {
            return operation.execute(); // executa a operação
        } catch (InvocationTargetException | IllegalAccessException | RemoteException e) {
            // servidor caiu
            log("Application server is down, retrying connection...");
            String str;
            if (!(str = reconnect()).contains("success")) {
                return str; // erro de conexão
            } else {
                try {
                    return operation.execute(); // tenta novamente
                } catch (RemoteException | InvocationTargetException | IllegalAccessException ignored) {}
            }
        }
        return "";
    }

    // reconecta com o servidor de aplicação
    private String reconnect() {
        // requisita nova conexão com a aplicação ao servidor de localizações
        try {
            LocationServerInterface loc = (LocationServerInterface)
                Naming.lookup("rmi://" + locationUrl);

            JSON request = JSON.create(url);
            appUrl = JSON.fromJson(loc.getApplication(request.toJson())).getMessageAs(String.class);
            String str = "Critical error: no application servers available";
            if (appUrl.equals(str)) {
                log(str);
                return str;
            }
            // reconecta com o servidor de aplicação
            app = (ApplicationServerInterface)
                Naming.lookup("rmi://" + appUrl);
            return "success";
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            String str = "Critical error: location server is down: " + e.getMessage();
            log(str);
            e.printStackTrace(System.err);
            return str;
        }
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
            System.out.print("Digite a url do servidor de localizações (ip:porta/serviço): ");
            new ProxyServer(reader.nextLine());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
