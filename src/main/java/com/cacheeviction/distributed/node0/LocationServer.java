package com.cacheeviction.distributed.node0;

import com.cacheeviction.distributed.global.server.Logger;
import com.cacheeviction.distributed.global.server.ProxyServerAddress;
import com.cacheeviction.distributed.global.util.JSON;
import com.cacheeviction.distributed.node1.ProxyServerInterface;
import com.cacheeviction.distributed.node2.ApplicationServerInterface;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

public class LocationServer extends UnicastRemoteObject implements LocationServerInterface {
    private final PriorityBlockingQueue<ProxyServerAddress> locations;
    private final Logger logger;
    private final List<String> secondaryApplicationUrls;
    private int port;
    private String primaryApplicationUrl;

    public LocationServer() throws RemoteException {
        port = 3000;
        locations = new PriorityBlockingQueue<>();
        logger = new Logger("locationServerLog.txt", false);
        secondaryApplicationUrls = new ArrayList<>();
        start();
    }

    private void start() {
        boolean started = false;
        while (!started) {
            try {
                String address = InetAddress.getLocalHost().getHostAddress();
                String url = address + ":" + port + "/LocationServer";

                LocateRegistry.createRegistry(port);
                Naming.rebind("rmi://" + url, this);

                log("Location Server started on " + url);
                started = true;
            } catch (RemoteException | MalformedURLException | UnknownHostException e) {
                log("Error starting location server: " + e.getMessage() + "\nRetrying on port " + ++port);
            }
        }
    }

    @Override
    public void addProxy(String address) throws RemoteException {
        // adiciona novo proxy
        address = JSON.fromJson(address).getMessageAs(String.class);
        ProxyServerAddress psa = new ProxyServerAddress(address, 0);
        if (!locations.contains(psa)) {
            log("Adding proxy " + address);
            locations.add(psa);
        } else {
            locations.remove(psa);
            locations.add(psa);
        }

        // para cada proxy, envia os endereços dos proxies disponíveis
        for (ProxyServerAddress location : locations) {
            String url = location.getAddress();

            try {
                ProxyServerInterface proxy = (ProxyServerInterface) Naming.lookup("rmi://" + url);

                // constrói a lista dos proxies disponíveis, exceto o atual
                List<String> availableProxies = locations.stream()
                    .map(ProxyServerAddress::getAddress)
                    .filter(a -> !a.equals(url))
                    .toList();

                log("Sending " + availableProxies + " to " + url);
                proxy.setProxies(JSON.create(availableProxies).toJson());
            } catch (NotBoundException | MalformedURLException e) {
                log("Failed to connect to proxy " + url + ": " + e.getMessage());
                locations.remove(location);
            }
        }

        log("Proxies: " + locations);
    }

    @Override
    public String getProxy() throws RemoteException {
        String clientHost;
        try {
            clientHost = getClientHost();
        } catch (Exception e) {
            String str = "Critical error: failed to get client host: " + e.getMessage();
            log(str);
            return JSON.create(str).toJson();
        }
        String location;
        if ((location = getLocation()) != null) {
            log("Client " + clientHost + " requested proxy: " + location);
            log("Proxies: " + locations);
            return JSON.create(location).toJson();
        }
        String str = "Critical error: no servers available";
        log(str);
        return JSON.create(str).toJson();
    }

    @Override
    public void addApplication(String url) throws RemoteException {
        ApplicationServerInterface app;
        if (primaryApplicationUrl != null) {
            // cadastra um servidor de aplicação secundário
            url = JSON.fromJson(url).getMessageAs(String.class);
            log("Secondary application added: " + url);
            secondaryApplicationUrls.add(url);

            // envia banco de dados atualizado para o servidor secundário
            try {
                ApplicationServerInterface primary = (ApplicationServerInterface)
                    Naming.lookup("rmi://" + primaryApplicationUrl);

                ApplicationServerInterface secondary = (ApplicationServerInterface)
                    Naming.lookup("rmi://" + url);

                secondary.setDatabase(primary.getDatabase());
            } catch (NotBoundException | MalformedURLException | RemoteException e) {
                log("Failed to connect to application: " + e.getMessage());
            }

            // testa as conexões dos servidores secundários
            for (String secUrl : secondaryApplicationUrls) {
                try {
                    Naming.lookup("rmi://" + secUrl);
                } catch (NotBoundException | MalformedURLException | RemoteException e) {
                    log("Secondary application server " + secUrl + " is down");
                    secondaryApplicationUrls.remove(secUrl);
                }
            }

            // envia a lista de servidores secundários para o servidor primário
            try {
                app = (ApplicationServerInterface) Naming.lookup("rmi://" + primaryApplicationUrl);
                log("Backup servers sent to " + primaryApplicationUrl + ": " + secondaryApplicationUrls);
                app.setBackups(JSON.create(secondaryApplicationUrls).toJson());
            } catch (MalformedURLException | NotBoundException | RemoteException e) {
                log("Failed to send secondary applications to primary: " + e.getMessage());
            }

            return;
        }
        synchronized (this) {
            primaryApplicationUrl = JSON.fromJson(url).getMessageAs(String.class);
        }
        try {
            Naming.lookup("rmi://" + primaryApplicationUrl);
            log("Primary application added: " + primaryApplicationUrl);
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            log("Failed to connect to primary application: " + e.getMessage());
        }
    }

    @Override
    public String getApplication(String proxyUrl) throws RemoteException {
        proxyUrl = JSON.fromJson(proxyUrl).getMessageAs(String.class);
        if (primaryApplicationUrl == null) {
            for (String secUrl : secondaryApplicationUrls) {
                try {
                    Naming.lookup("rmi://" + secUrl);

                    synchronized (this) {
                        primaryApplicationUrl = secUrl;
                    }

                    log(proxyUrl + " requested application: " + primaryApplicationUrl);
                    return JSON.create(primaryApplicationUrl).toJson();
                } catch (NotBoundException | MalformedURLException | RemoteException e) {
                    secondaryApplicationUrls.remove(secUrl);
                    log("Secondary application server " + secUrl + " is down");
                }
            }

            String str = "Critical error: no application servers available";
            log(str);

            // remove o proxy da fila, pois não há servidores disponíveis
            locations.remove(new ProxyServerAddress(proxyUrl, 0));
            return JSON.create(str).toJson();
        } else {
            // testa a conexão antes de retornar
            try {
                Naming.lookup("rmi://" + primaryApplicationUrl);
            } catch (NotBoundException | MalformedURLException | RemoteException e) {
                log("Primary application server " + primaryApplicationUrl + " is down");
                primaryApplicationUrl = null;
                for (String secUrl : secondaryApplicationUrls) {
                    try {
                        Naming.lookup("rmi://" + secUrl);

                        synchronized (this) {
                            primaryApplicationUrl = secUrl;
                        }

                        log(proxyUrl + " requested application: " + primaryApplicationUrl);
                        return JSON.create(primaryApplicationUrl).toJson();
                    } catch (NotBoundException | MalformedURLException | RemoteException ex) {
                        secondaryApplicationUrls.remove(secUrl);
                        log("Secondary application server " + secUrl + " is down");
                    }
                }

                String str = "Critical error: no application servers available";
                log(str);

                // remove o proxy da fila, pois não há servidores disponíveis
                locations.remove(new ProxyServerAddress(proxyUrl, 0));
                return JSON.create(str).toJson();
            }
        }
        log(proxyUrl + " requested application: " + primaryApplicationUrl);
        return JSON.create(primaryApplicationUrl).toJson();
    }

    @Override
    public void updateWorkload(String address) throws RemoteException {
        ProxyServerAddress location = locations.stream()
            .filter(l -> l.getAddress().equals(address))
            .findFirst()
            .orElse(null);
        if (location == null) {
            log("Proxy " + address + " not found");
            return;
        }
        synchronized (this) {
            location.setWorkload(location.getWorkload() - 1);
        }
        locations.remove(location);
        locations.add(location);
        log("Proxies: " + locations);
    }

    private String getLocation() {
        while (true) {
            ProxyServerAddress location = locations.poll();
            if (location == null) {
                if (primaryApplicationUrl != null) {
                    log("No proxy servers available, redirecting to " + primaryApplicationUrl);
                }
                return primaryApplicationUrl;
            }
            // testa a conexão antes de retornar
            try {
                Naming.lookup("rmi://" + location.getAddress());

                // atualiza carga
                synchronized (this) {
                    location.setWorkload(location.getWorkload() + 1);
                }

                locations.add(location);
                return location.getAddress();
            } catch (NotBoundException | MalformedURLException | RemoteException e) {
                log("Proxy " + location.getAddress() + " is down");
            }
        }
    }

    private void log(String s) {
        logger.append(s);
        System.out.println(s);
    }

    public static void main(String[] args) {
        try {
            new LocationServer();
        } catch (RemoteException e) {
            System.err.println("Failed to start location server: " + e.getMessage());
        }
    }
}
