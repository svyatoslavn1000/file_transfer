package ru.geekbrains.netty.client;

import ru.geekbrains.netty.common.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import javax.xml.soap.Text;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private String nick;

    @FXML
    ListView<String> clientFilesList;

    @FXML
    ListView<String> serverFilesList;

    @FXML
    HBox cloudPanel;

    @FXML
    HBox authPanel;

    @FXML
    TextField loginField;

    @FXML
    PasswordField passwordField;

    @FXML
    Button authButton;

    @FXML
    TextField loginField1;

    @FXML
    TextField nickField;

    @FXML
    PasswordField passwordField1;

    @FXML
    PasswordField passwordField2;

    @FXML
    Button registerButton;



    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);
        Network.start();
        Thread thread = new Thread(() -> {

            try {
                while (true) {
                    AbstractMessage abstractMessage = Network.readObject();
                    if(abstractMessage instanceof  RegistrationMessage){
                        RegistrationMessage r = (RegistrationMessage) abstractMessage;
                        if (r.message.equals("/not_null_userId")) {
                             Platform.runLater(() -> registerButton.setText("Ник занят."));
                        } else {
                            String nick = r.message.split(" ")[1];
                            Files.createDirectory(Paths.get("client" + nick));
                               Platform.runLater(() -> registerButton.setText("Регистрация успешно завершена."));
                        }
                    }
                    if (abstractMessage instanceof AuthMessage) {
                        AuthMessage authMessage = (AuthMessage) abstractMessage;
                        if (authMessage.message.startsWith("/authOk")) {
                            setAuthorized(true);
                            nick = authMessage.message.split(" ")[1];
                            System.out.println("Подключился клиент " + nick);
                            break;
                        }
                        if ("/null_userId".equals(authMessage.message)) {
                            Platform.runLater(() -> authButton.setText("Неверный логин или пароль."));
                        }
                    }
                }
                Network.sendMsg(new RefreshServerFileListMessage());
                refreshLocalFilesList();
                while (true) {
                    AbstractMessage abstractMessage = Network.readObject();
                    if (abstractMessage instanceof FileMessage) {
                        FileMessage fileMessage = (FileMessage) abstractMessage;
                        if (!Files.exists(Paths.get("client" + nick + "/" + fileMessage.getFilename()))) {
                            Files.write(Paths.get("client" + nick + "/" + fileMessage.getFilename()),
                                    fileMessage.getData(), StandardOpenOption.CREATE);
                            refreshLocalFilesList();
                        }
                    }
                    if (abstractMessage instanceof RefreshServerFileListMessage) {
                        RefreshServerFileListMessage refreshServerMsg = (RefreshServerFileListMessage) abstractMessage;
                        refreshServerFileList(refreshServerMsg.getServerFileList());
                    }

                }

            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            } finally {
                Network.stop();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void setAuthorized(boolean isAuthorized) {
        if (!isAuthorized) {
            authPanel.setVisible(true);
            authPanel.setManaged(true);
            cloudPanel.setVisible(false);
            cloudPanel.setManaged(false);
        } else {
            authPanel.setVisible(false);
            authPanel.setManaged(false);
            cloudPanel.setVisible(true);
            cloudPanel.setManaged(true);
        }
    }

    public void tryToAuth() {
        Network.sendMsg(new AuthMessage(loginField.getText(), passwordField.getText()));
        loginField.clear();
        passwordField.clear();
    }

    public void pressOnDownloadButton(ActionEvent actionEvent) {
        Network.sendMsg(new DownloadMessage(serverFilesList.getSelectionModel().getSelectedItem()));
    }

    public void pressOnSendToCloudButton(ActionEvent actionEvent) {
        try {
            Network.sendMsg(new FileMessage(Paths.get("client" + nick + "/"
                    + clientFilesList.getSelectionModel().getSelectedItem())));
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public void deleteFromClient(ActionEvent actionEvent) {
        try {
            Files.delete(Paths.get("client" + nick + "/" + clientFilesList.getSelectionModel().getSelectedItem()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        refreshLocalFilesList();
    }

    public void deleteFromServer(ActionEvent actionEvent) {
        Network.sendMsg(new DeleteMessage(serverFilesList.getSelectionModel().getSelectedItem()));
    }

    private void refreshLocalFilesList() {
        updateUI(() -> {
            try {
                clientFilesList.getItems().clear();
                Files.list(Paths.get("client" + nick)).map(p -> p.getFileName().toString()).
                        forEach(o -> clientFilesList.getItems().add(o));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void refreshServerFileList(ArrayList<String> fileList) {
        updateUI(() -> {
            serverFilesList.getItems().clear();
            serverFilesList.getItems().addAll(fileList);
        });
    }

    private static void updateUI(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }



    public void registrationOnServer(ActionEvent actionEvent) {
        if (passwordField1.getText().equals(passwordField2.getText())) {
            Network.sendMsg(new RegistrationMessage(loginField1.getText(), passwordField1.getText(), nickField.getText()));
        } else {
            System.out.println("Введите еще раз.");
        }
    }


    public void closeConnection(ActionEvent actionEvent) {
        Network.sendMsg(new AuthMessage("/connection_close"));
        MainClient.launch();

    }
}

