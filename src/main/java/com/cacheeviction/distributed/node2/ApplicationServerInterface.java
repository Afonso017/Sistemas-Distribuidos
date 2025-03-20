package com.cacheeviction.distributed.node2;

import com.cacheeviction.distributed.global.server.ServerInterface;

import java.rmi.RemoteException;

public interface ApplicationServerInterface extends ServerInterface {

    String request(String message) throws RemoteException;
    String getUsers() throws RemoteException;
    String getSize() throws RemoteException;
    String getDatabaseArrayListString() throws RemoteException;
    String getDatabaseObservableList() throws RemoteException;
    String readLog() throws RemoteException;
    void backupRequest(String message) throws RemoteException;
    void setBackups(String json) throws RemoteException;
    String getDatabase() throws RemoteException;
    void setDatabase(String database) throws RemoteException;
}
