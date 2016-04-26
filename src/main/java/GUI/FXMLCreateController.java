/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import GUI.messages.SendCreationRequest;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;

/**
 * FXML Controller class
 *
 * @author francescop
 */
public class FXMLCreateController implements Initializable {
    
    @FXML
    private TextField file_name;
    @FXML
    private TextField tag1;
    @FXML
    private TextField tag2;
    @FXML
    private TextField tag3;
    @FXML
    private TextField tag4;
    /**
     * Initializes the controller class.
     */
    @FXML
    private void create(ActionEvent event) {
        String newFileName = file_name.getText();
        file_name.setText(newFileName);
        if(!newFileName.isEmpty()){
            System.out.println("You clicked Create into the new window!");
            System.out.println("Started create request");
            
            //TODO: validate the tags
            //set the ModifiedFile
            List<String> tags = new ArrayList<>();
            String tag = tag1.getText();
            //if the tag is not empty
            if(!tag.isEmpty()) tags.add(tag);
            tag = tag2.getText();
            //if the tag is not empty and is not already present in the list
            if(!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
            tag = tag3.getText();
            if(!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
            tag = tag4.getText();
            if(!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
            GUI.OpenedFile.set(newFileName, tags);
            
            //chech for existing files with the same name
            GuiActor.getClusterListenerActorRef().tell(new SendCreationRequest(newFileName), GuiActor.getGuiActorRef());
        }else{
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Empty name");
            alert.setContentText("The name of the new file must contain at least a character!");

            alert.showAndWait();
        }
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
    }    
}
