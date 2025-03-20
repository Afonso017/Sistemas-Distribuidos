module com.cacheeviction.distributed {
    requires com.google.gson;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.rmi;
    requires java.xml;

    exports com.cacheeviction.distributed.client;
    exports com.cacheeviction.distributed.global.server;
    exports com.cacheeviction.distributed.global.util;
    exports com.cacheeviction.distributed.global.structure;
    exports com.cacheeviction.distributed.node0;
    exports com.cacheeviction.distributed.node1;
    exports com.cacheeviction.distributed.node2;

    opens com.cacheeviction.distributed.client to com.google.gson, javafx.fxml, java.rmi;
    opens com.cacheeviction.distributed.global.util to com.google.gson;
    opens com.cacheeviction.distributed.global.server to com.google.gson;
    opens com.cacheeviction.distributed.node0 to java.rmi, com.google.gson;
    opens com.cacheeviction.distributed.node1 to java.rmi, com.google.gson;
    opens com.cacheeviction.distributed.node2 to java.rmi, com.google.gson;
}
