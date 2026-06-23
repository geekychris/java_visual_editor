package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

public class ContactFormController {
    @FXML
    private CheckBox activeCheckBox;
    @FXML
    private Spinner ageSpinner;
    @FXML
    private DatePicker birthdayDatePicker;
    @FXML
    private TextField classTextField;
    @FXML
    private TextField emailTextField;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField notesTextField;

    public void bind(Contact pojo) {
        activeCheckBox.setSelected(pojo.isActive());
        ageSpinner.getValueFactory().setValue(pojo.getAge());
        birthdayDatePicker.setValue(pojo.getBirthday());
// TODO: bind class (Class<?>) to classTextField
        emailTextField.setText(pojo.getEmail() == null ? "" : pojo.getEmail());
        nameTextField.setText(pojo.getName() == null ? "" : pojo.getName());
        notesTextField.setText(pojo.getNotes() == null ? "" : pojo.getNotes());
    }

    public void save(Contact pojo) {
        pojo.setActive(activeCheckBox.isSelected());
        pojo.setAge(((Integer) ageSpinner.getValueFactory().getValue()));
        pojo.setBirthday(birthdayDatePicker.getValue());
        pojo.setEmail(emailTextField.getText());
        pojo.setName(nameTextField.getText());
        pojo.setNotes(notesTextField.getText());
    }

    @FXML
    private void birthdayDatePickerMouseClicked(MouseEvent event) {
// Sample interactions with birthdayDatePicker — uncomment what you need:
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
