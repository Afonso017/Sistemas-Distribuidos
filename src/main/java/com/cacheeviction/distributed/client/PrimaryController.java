package com.cacheeviction.distributed.client;

import com.cacheeviction.distributed.global.util.KMP;
import com.cacheeviction.distributed.global.util.ServiceOrder;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Objects;

public class PrimaryController {
    public static ServiceOrder selected;
    private final ObservableList<String> names = FXCollections.observableArrayList();

    @FXML
    public TextArea textLog;

    @FXML
    public TextField searchLogFld;

    @FXML
    public TabPane tabPane;

    @FXML
    private Label qtd;

    @FXML
    private TextField idFld;

    @FXML
    private TextField nameFld;

    @FXML
    private TextField descFld;

    @FXML
    private TextField searchFld;

    @FXML
    private ListView<ServiceOrder> listView;

    @FXML
    private Button fillDatabaseBtn;

    @FXML
    private void fillDatabase() {
        Client.instance.fillDatabase();
    }

    @FXML
    private Button fillCacheBtn;

    @FXML
    private void fillCache() {
        Client.instance.fillCache();
    }

    @FXML
    private void searchLog() {
        String searchField = searchLogFld.getText();
        if (searchField == null || searchField.isEmpty()) {
            textLog.setText(Client.instance.readLog());
            return;
        }

        List<String> search = KMP.search(searchField, Client.instance.readLog());
        StringBuilder sb = new StringBuilder();
        for (String s : search) {
            sb.append(s).append("\n");
        }
        textLog.setText(sb.toString());
    }

    @FXML
    private void initialize() {
        ObservableList<ServiceOrder> list = FXCollections.observableArrayList(Client.instance.getDatabaseObservableList());

        // define a quantidade de registros
        qtd.setText("Registros: " + list.size());

        // define a lista de registros
        listView.setItems(FXCollections.observableArrayList(Objects.requireNonNull(list)));

        // define o listener para a lista de registros
        listView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem edit = new MenuItem("Editar");

            var editImageView = new ImageView(new Image(Objects.requireNonNull(getClass()
                .getResourceAsStream("/edit.png"))));
            editImageView.setFitWidth(25);
            editImageView.setFitHeight(25);
            edit.setGraphic(editImageView);

            edit.setOnAction(e -> {
                selected = listView.getSelectionModel().getSelectedItem();

                Stage modalStage = new Stage();
                modalStage.initModality(Modality.APPLICATION_MODAL);
                modalStage.initOwner(Client.getStage());
                modalStage.setTitle("Editar Ordem de Serviço");
                modalStage.setResizable(false);

                Scene scene = null;
                try {
                    scene = new Scene(Client.loadFXML("EditarOS"), 400, 300);
                } catch (IOException ex) {
                    System.err.println("Erro ao carregar a tela de edição de ordem de serviço.");
                }
                modalStage.setScene(scene);
                modalStage.show();
            });

            MenuItem remove = new MenuItem("Excluir");

            var removeImageView = new ImageView(new Image(Objects.requireNonNull(getClass()
                .getResourceAsStream("/trash.png"))));
            removeImageView.setFitWidth(25);
            removeImageView.setFitHeight(25);
            remove.setGraphic(removeImageView);

            remove.setOnAction(e -> {
                String response = Client.instance.sendMessage("remove", listView.getSelectionModel().getSelectedItem()).getMessageAs(String.class);
                if (response.equals("success")) {
                    refreshListView();
                } else {
                    Client.callDialogPane("Error", "Você deve ter permissões de administrador "
                        + "para excluir registros!").showAndWait();
                }
            });

            contextMenu.getItems().addAll(edit, remove);
            listView.setContextMenu(contextMenu);
        });

        if (Client.isAdmin) {
            try {
                Parent root = Client.loadFXML("ServerLogTab");
                TabPane tabPaneRoot = (TabPane) root.lookup("#tabPane");
                Tab serverLogTab = tabPaneRoot.getTabs().get(1);
                tabPane.getTabs().add(serverLogTab);

                // define o texto do log
                textLog = (TextArea) serverLogTab.getContent().lookup("#textLog");
                textLog.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14; -fx-text-fill: #000000;");
                textLog.setText(Client.instance.readLog());

                // adiciona onAction para o campo de busca do log
                searchLogFld = (TextField) serverLogTab.getContent().lookup("#searchLogFld");
                searchLogFld.setOnAction(e -> searchLog());
            } catch (IOException e) {
                Client.callDialogPane("Error", "Erro ao carregar o tab de log do servidor: "
                    + e.getMessage()).showAndWait();
                e.printStackTrace(System.err);
            }
        } else {
            fillDatabaseBtn.setVisible(false);
            fillCacheBtn.setVisible(false);
        }
    }

    @FXML
    private void registerSO() {
        try {
            ServiceOrder so = new ServiceOrder(Integer.parseInt(idFld.getText()), nameFld.getText(), descFld.getText());
            String response = Client.instance.sendMessage("register", so).getMessageAs(String.class);
            if (response.equals("success")) {
                Client.callDialogPane("Message", "Ordem de serviço registrada com sucesso!")
                    .showAndWait();
            } else if (response.equals("already exists")) {
                Client.callDialogPane("Error", "Já existe uma ordem de serviço com o ID informado!")
                    .showAndWait();
            }
        } catch (Exception e) {
            Client.callDialogPane("Error", "Erro ao registrar a ordem de serviço: "
                + e.getMessage()).showAndWait();
            e.printStackTrace(System.err);
        }
        refreshListView();
    }

    @FXML
    private void search() {
        String newValue = searchFld.getText();
        if (newValue == null || newValue.isEmpty()) {
            refreshListView();
        } else {
            try {
                Integer key = Integer.valueOf(newValue.trim());

                ServiceOrder so = Client.instance.sendMessage("search", key).getMessageAs(ServiceOrder.class);

                if (so != null && so.getName() != null) {
                    ObservableList<ServiceOrder> searchResult = FXCollections.observableArrayList(so);
                    listView.setItems(searchResult);
                } else {
                    listView.setItems(FXCollections.observableArrayList());
                }
            } catch (NumberFormatException e) {
                listView.setItems(FXCollections.observableArrayList());
                Client.callDialogPane("Error", "O valor de busca deve ser numérico.").showAndWait();
            }
        }
    }

    public void refreshListView() {
        ObservableList<ServiceOrder> list = FXCollections.observableArrayList(Client.instance.getDatabaseObservableList());
        qtd.setText("Registros: " + list.size());
        names.clear();
        names.addAll(Client.instance.getDatabaseArrayListString());
        listView.setItems(list);
    }
}
