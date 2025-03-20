package com.cacheeviction.distributed.node1;

import com.cacheeviction.distributed.global.server.ServerInterface;

import java.rmi.RemoteException;

public interface ProxyServerInterface extends ServerInterface {

    // métodos de comunicação entre servidor de localização e proxy
    void setProxies(String address) throws RemoteException;

    // métodos de comunicação de clientes
    String request(String message) throws RemoteException;

    // métodos de comunicação entre proxies
    String p2pRequest(String message) throws RemoteException;
}
