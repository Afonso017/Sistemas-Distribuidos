package com.cacheeviction.distributed.client;

import com.cacheeviction.distributed.global.util.Auth;
import com.cacheeviction.distributed.global.util.JSON;
import com.cacheeviction.distributed.global.util.ServiceOrder;
import com.cacheeviction.distributed.node0.LocationServerInterface;
import com.cacheeviction.distributed.node1.ProxyServerInterface;
import com.cacheeviction.distributed.node2.ApplicationServerInterface;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;

public class Client extends Application {
    public static Client instance;
    public static boolean isAdmin = false;
    public static boolean isProxy = false;

    private static Stage stage;
    private static Label labelError;
    private static Label labelMessage;
    private static HashSet<Integer> insertedIds;
    private static String locationUrl;

    private ApplicationServerInterface app;
    private ProxyServerInterface proxy;
    static Auth auth;

    public Client() {
        auth = new Auth();
        instance = this;
    }

    // inicializa a conexão com o servidor e a interface gráfica
    private void start() {
        System.out.println("Connecting to location server...");

        // envia requisição para servidor de localização
        String url = "";
        try {
            String response = ((LocationServerInterface) Naming.lookup("rmi://" + locationUrl)).getProxy();

            url = JSON.fromJson(response).getMessageAs(String.class);

            if (!url.contains("error")) {
                if (url.contains("Proxy")) {
                    System.out.println("Connecting to proxy server at " + url);
                    proxy = (ProxyServerInterface) Naming.lookup("rmi://" + url);
                    isProxy = true;
                } else {
                    System.out.println("Connecting to application server at " + url);
                    app = (ApplicationServerInterface) Naming.lookup("rmi://" + url);
                }
            } else {
                System.err.println(url);
                callDialogPane("Error", "Erro ao se conectar com a aplicação: " + url)
                    .showAndWait();
            }
        } catch (IOException | NotBoundException e) {
            System.err.println("Failed to connect to server " + url);
            System.exit(1);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parameters params = getParameters();
        List<String> args = params.getRaw();

        System.out.println("Argumentos recebidos: " + args);
        locationUrl = args.getFirst();
        start();

        Scene scene = new Scene(loadFXML("Login"), 640, 480);
        stage.setTitle("Sistema de Gerenciamento de Ordens de Serviço");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST,
            (e) -> {
                sendMessage("close", null);
                System.exit(0);
            });
        stage.show();
        Client.stage = stage;

        // label de mensagem
        labelMessage = new Label();
        labelMessage.setFont(Font.font("Consolas", 15));

        // label de mensagem de erro
        labelError = new Label();
        labelError.setTextFill(Paint.valueOf("#ff0606"));
        labelError.setFont(Font.font("Consolas", 15));
    }

    // envia uma mensagem para o servidor de proxy ou aplicação
    public JSON sendMessage(String operation, Object message) {
        JSON request = JSON.create(operation, message, auth);
        String response;
        if (isProxy) {
            try {
                response = proxy.request(request.toJson());
                return JSON.fromJson(response);
            } catch (RemoteException e) {
                if (!request.getOperation().equals("close")) {
                    reconnect();
                    try {
                        response = proxy.request(request.toJson());
                        return JSON.fromJson(response);
                    } catch (RemoteException ex) {
                        callDialogPane("Error", "Erro ao enviar mensagem para o servidor proxy")
                            .showAndWait();
                        e.printStackTrace(System.err);
                    }
                }
            }
        } else {
            try {
                response = app.request(request.toJson());
                return JSON.fromJson(response);
            } catch (RemoteException e) {
                if (!request.getOperation().equals("close")) {
                    reconnect();
                    try {
                        response = app.request(request.toJson());
                        return JSON.fromJson(response);
                    } catch (RemoteException ex) {
                        callDialogPane("Error", "Erro ao enviar mensagem para o servidor de aplicação")
                            .showAndWait();
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
        return null;
    }

    public static void reconnect() {
        instance.start();
    }

    // envia requisição de login e retorna se foi autenticado
    public boolean login(String username, String password) {
        auth.setUsername(username);
        auth.setPassword(password);
        JSON json = sendMessage("auth", auth);
        auth.setRole(json.getAuth().getRole());
        String response = json.getMessageAs(String.class);
        if (Objects.equals(response, "authenticated")) {
            Client.isAdmin = auth.getRole().equals("admin");
            return true;
        }
        return false;
    }

    // troca de tela
    public static void switchPage(String page) {
        try {
            Scene scene = new Scene(loadFXML(page), 640, 480);
            stage.setScene(scene);
        } catch (IOException e) {
            callDialogPane("Error", "Erro ao carregar a página " + page)
                .showAndWait();
            e.printStackTrace(System.err);
        }
    }

    // função para carregar o arquivo FXML
    public static <T> T loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static Stage getStage() {
        return stage;
    }

    // função para criar um popup de diálogo
    public static Dialog<DialogPane> callDialogPane(String typeMessage, String message) {
        DialogPane dp = new DialogPane();
        Dialog<DialogPane> dialog = new Dialog<>();
        switch (typeMessage) {
            case "Error":
                labelError.setText(message);
                dp.setContent(labelError);
                dialog.setDialogPane(dp);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                break;
            case "Message":
                labelMessage.setText(message);
                dp.setContent(labelMessage);
                dialog.setDialogPane(dp);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                break;
            case "Dialog":
                labelMessage.setText(message);
                dp.setContent(labelMessage);
                dialog.setDialogPane(dp);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.YES);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
                break;
        }
        return dialog;
    }

    // métodos auxiliares para administrador
    // preenche o banco com 100 inserções aleatórias sem repetições
    public void fillDatabase() {
        Random random = new Random();
        insertedIds = new HashSet<>();

        while (insertedIds.size() < 100) {
            int id = random.nextInt(1000);
            if (!insertedIds.contains(id)) {
                insertedIds.add(id);
                ServiceOrder so = new ServiceOrder(id, "Ordem de Serviço " + id,
                    "Descrição da Ordem de Serviço " + id);
                sendMessage("register", so);
            }
        }
    }

    // preenche a cache com 30 buscas de elementos adicionados ao banco
    public void fillCache() {
        if (insertedIds.isEmpty()) {
            Random random = new Random();
            while (insertedIds.size() < 30) {
                int id = random.nextInt(100);
                insertedIds.add(id);
            }
        }

        ArrayList<Integer> ids = new ArrayList<>(insertedIds);
        for (int i = 0; i < Math.min(30, ids.size()); i++) {
            sendMessage("search", ids.get(i));
        }
    }

    public String readLog() {
        return sendMessage("readLog", null).getMessageAs(String.class);
    }

    @SuppressWarnings("unchecked")
    public <U> List<U> getDatabaseArrayListString() {
        List<U> list = (List<U>) sendMessage("getDatabaseArrayListString", null).getMessageAs(List.class);
        return new ArrayList<>(list); // converte a lista para ArrayList
    }

    public ArrayList<ServiceOrder> getDatabaseObservableList() {
        Type listType = new TypeToken<ObservableList<ServiceOrder>>() {}.getType();
        return sendMessage("getDatabaseObservableList", null).getMessageAs(listType);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
