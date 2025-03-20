package com.cacheeviction.distributed.client;

import com.cacheeviction.distributed.global.util.ServiceOrder;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Objects;

public class EditSOController {

    @FXML
    private TextField nameFld;

    @FXML
    private TextField descFld;

    @FXML
    private Button editBtn;

    @FXML
    private Button cancelBtn;

    @FXML
    private void initialize() {
        nameFld.setText(PrimaryController.selected.getName());
        descFld.setText(PrimaryController.selected.getDescription());
    }

    @FXML
    private void editSO() {
        ServiceOrder updatedSO = new ServiceOrder(
            PrimaryController.selected.getCode(),
            nameFld.getText(),
            descFld.getText()
        );

        try {
            String response = Client.instance.sendMessage("edit", updatedSO).getMessageAs(String.class);
            if (Objects.equals(response, "forbidden")) {
                Client.callDialogPane("Error", "Você deve ter permissões de administrador para"
                    + " editar registros!").showAndWait();
            }
        } catch (Exception e) {
            Client.callDialogPane("Error", "Erro ao editar ordem de serviço: "
                + e.getMessage()).showAndWait();
            e.printStackTrace(System.err);
        }

        ((Stage) editBtn.getScene().getWindow()).close();
    }

    @FXML
    private void cancel() {
        ((Stage) cancelBtn.getScene().getWindow()).close();
    }
}
