package com.cacheeviction.distributed.client;

import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    public TextField usernameField;
    public PasswordField passwordField;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        usernameField.setOnAction(this::handleLogin);
        passwordField.setOnAction(this::handleLogin);
    }

    public void handleLogin(ActionEvent e) {
        if (Client.instance.login(usernameField.getText(), passwordField.getText())) {
            Client.switchPage("Home");
        } else {
            Client.callDialogPane("Error", "Usuário/senha inválidos!").showAndWait();
        }
    }
}
