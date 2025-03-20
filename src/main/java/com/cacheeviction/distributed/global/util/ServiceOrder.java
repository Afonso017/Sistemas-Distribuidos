package com.cacheeviction.distributed.global.util;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServiceOrder implements Comparable<ServiceOrder>, Serializable {
    private final int code;
    private String name;
    private String description;
    private final String date;

    public ServiceOrder(final int code, final String name, final String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy-HH:mm:ss"));
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "{ " + code + " | " + name + " | " + description + " | " + date + " }";
    }

    @Override
    public int hashCode() {
        int result = 37 * code;
        result += 31 * name.hashCode();
        result += 11 * description.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Integer) {
            return this.code == (int) obj;
        } else if (!(obj instanceof ServiceOrder)) {
            return false;
        }
        ServiceOrder so = (ServiceOrder) obj;
        return this.code == so.code;
    }

    @Override
    public int compareTo(ServiceOrder so) {
        return this.code - so.code;
    }
}
