package com.cacheeviction.distributed.node0;

import com.cacheeviction.distributed.global.server.ServerInterface;

import java.rmi.RemoteException;

public interface LocationServerInterface extends ServerInterface {

    void addProxy(String address) throws RemoteException;
    String getProxy() throws RemoteException;
    void addApplication(String url) throws RemoteException;
    String getApplication(String proxyAddress) throws RemoteException;
    void updateWorkload(String address) throws RemoteException;
}
