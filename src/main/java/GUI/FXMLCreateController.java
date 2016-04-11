/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import GUI.messages.SendCreationRequest;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 * FXML Controller class
 *
 * @author francescop
 */
public class FXMLCreateController implements Initializable {
    
    @FXML
    private TextField file_name;
    /**
     * Initializes the controller class.
     */
    @FXML
    private void create(ActionEvent event) {
        String searchText = file_name.getText();
        file_name.setText(searchText);
        if(!searchText.isEmpty()){
            System.out.println("You clicked Create into the new window!");
            System.out.println("You clicked Modify!");
            GuiActor.getClusterListenerActorRef().tell(new SendCreationRequest(searchText), GuiActor.getGuiActorRef());
            //chech for existing files with the same name

            //this has to be done in another message...
            try {
                FXMLMainController.getSecondaryStage().close();
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Modify.fxml"));
                Parent root = (Parent) fxmlLoader.load();
                Stage secondaryStage = new Stage();
                secondaryStage.setScene(new Scene(root));

                //remove "minimize" and "restore down" buttons
                secondaryStage.initStyle(StageStyle.UTILITY);
                //disable close button
                secondaryStage.setOnCloseRequest((final WindowEvent windowEvent) -> { windowEvent.consume(); });

                FXMLMainController.setSecondaryStage(secondaryStage);
                secondaryStage.show();
                //((Stage) label.getScene().getWindow()).hide();
            } catch(Exception ex) {}
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
