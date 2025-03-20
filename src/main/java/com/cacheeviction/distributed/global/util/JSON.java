package com.cacheeviction.distributed.global.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.Serializable;
import java.lang.reflect.Type;

public class JSON implements Serializable {
    private static final Gson gson = new Gson();
    private static final Gson prettyJson = new GsonBuilder().setPrettyPrinting().create();

    private final String operation;
    private final String message;
    private final String auth;

    private JSON(String operation, String message, String auth) {
        this.operation = operation;
        this.message = message;
        this.auth = auth;
    }

    private JSON(String message) {
        this.operation = null;
        this.message = message;
        this.auth = null;
    }

    public Auth getAuth() {
        return gson.fromJson(auth, Auth.class);
    }

    public String getOperation() {
        return operation;
    }

    public static JSON create(String operation, Object message, Auth auth) {
        return new JSON(operation, gson.toJson(message), gson.toJson(auth));
    }

    public static JSON create(Object message) {
        return new JSON(gson.toJson(message));
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static JSON fromJson(String json) throws JsonSyntaxException {
        return gson.fromJson(json, JSON.class);
    }

    public <T> T getMessageAs(Class<T> clazz) throws JsonSyntaxException {
        return gson.fromJson(this.message, clazz);
    }

    public <T> T getMessageAs(Type type) throws JsonSyntaxException {
        return gson.fromJson(this.message, type);
    }

    public static String toPrettyJson(Object object) {
        return prettyJson.toJson(object);
    }

    public static <T> T fromPrettyJson(String json, Type type) throws JsonSyntaxException {
        return prettyJson.fromJson(json, type);
    }

    @Override
    public String toString() {
        return prettyJson.toJson(this);
    }
}
