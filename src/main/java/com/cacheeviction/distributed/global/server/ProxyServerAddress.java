package com.cacheeviction.distributed.global.server;

import java.io.Serializable;

public class ProxyServerAddress implements Comparable<ProxyServerAddress>, Serializable {
    private final String address;
    private int workload;

    public ProxyServerAddress(String address, int workload) {
        this.address = address;
        this.workload = workload;
    }

    public String getAddress() {
        return address;
    }

    public int getWorkload() {
        return workload;
    }

    public void setWorkload(int workload) { this.workload = workload; }

    @Override
    public int compareTo(ProxyServerAddress o) {
        return Integer.compare(this.workload, o.workload);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProxyServerAddress psa) {
            return address.equals(psa.address);
        }
        return false;
    }

    @Override
    public String toString() {
        return address + " " + workload;
    }
}
