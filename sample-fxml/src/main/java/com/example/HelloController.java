package com.example;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;

public class HelloController {
    @javafx.fxml.FXML
    private javafx.scene.control.Button submitBtn;
    @FXML
    private TextField nameField;
    @FXML
    private CheckBox agreeCheck;
    @FXML
    private Label title;
    @FXML
    private Label status;
    @FXML
    private CheckBox checkBox1;
    @FXML
    private CheckBox mine;
    @FXML
    private ScrollPane scrollPane1;
    @FXML
    private TabPane tabPane1;
    @FXML
    private HBox hBox2;
    @FXML
    private Button button4;
    @FXML
    private Button button5;
    @FXML
    private CheckBox checkBox2;
    @FXML
    private ProgressBar progressBar1;

    private double progress= 0;
    @FXML
    private void submitBtnAction(ActionEvent event) {
        System.out.println("btn Mouse exited");
        progress+=0.1;
        progressBar1.setProgress(progress);

        /*
        @FXML private Button saveBtn;

@FXML
private void onSave(ActionEvent event) {
    saveBtn.setDisable(true);             // prevent double-submit
    saveBtn.setText("Saving…");
    doBackgroundSave().whenComplete((r, err) -> Platform.runLater(() -> {
        saveBtn.setDisable(false);
        saveBtn.setText("Save");
    }));
}
         */
    }

    @javafx.fxml.FXML
    private void submitBtnMouseExited(javafx.scene.input.MouseEvent event) {
        System.out.println("btn Mouse exited");
    }

    @FXML
    private void submitBtnMousePressed(MouseEvent event) {
        System.out.println("btn Mouse pressed");
    }

    @FXML
    private void nameFieldKeyTyped(KeyEvent event) {
        System.out.println("fieldKeyTyped");
    }

    @FXML
    private void agreeCheckAction(ActionEvent event) {
    }

    @FXML
    private void titleMouseClicked(MouseEvent event) {
    }

    @FXML
    private void statusMouseClicked(MouseEvent event) {
    }

    @FXML
    private void checkBox1Action(ActionEvent event) {
    }

    @FXML
    private void mineAction(ActionEvent event) {
        if (mine.isSelected()) { System.out.println("selected"); }
        else { System.out.println("de selected"); }
    }

    @FXML
    private void scrollPane1MouseClicked(MouseEvent event) {
    }

    @FXML
    private void tabPane1MouseClicked(MouseEvent event) {
    }

    @FXML
    private void hBox2MouseClicked(MouseEvent event) {
    }

    @FXML
    private void initialize() {
        button4.visibleProperty().bind(mine.selectedProperty());
    }

    @FXML
    private void onButton4ChooseFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        File picked = chooser.showOpenDialog(button4.getScene().getWindow());
        if (picked != null) { /* TODO: use picked file */ }
    }

    @FXML
    private void button5Action(ActionEvent event) {
// // Button clicked
// System.out.println("button5 clicked");
    }

    @FXML
    private void checkBox2Action(ActionEvent event) {
// Sample interactions with checkBox2 — uncomment what you need:
//
// boolean checked = checkBox2.isSelected();
// if (checked) {
//     // checkBox2 was just checked
// } else {
//     // checkBox2 was just unchecked
// }
//
// // Modify state:
// checkBox2.setSelected(!checked);
// checkBox2.setText("New label");
// checkBox2.setIndeterminate(false);
// checkBox2.setDisable(true);
    }

    @FXML
    private void progressBar1MouseClicked(MouseEvent event) {
// Sample interactions with progressBar1 — uncomment what you need:
//
// // Read / set progress (0.0 – 1.0, or -1 for indeterminate):
// double current = progressBar1.getProgress();
// progressBar1.setProgress(0.5);
// progressBar1.setProgress(javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);
//
// MouseEvent details:
// double x = event.getX();             // relative to source
// double y = event.getY();
// javafx.scene.input.MouseButton btn = event.getButton();
// int clicks = event.getClickCount();
// boolean shift = event.isShiftDown();
// boolean ctrl  = event.isControlDown();
// boolean alt   = event.isAltDown();
    }
}
