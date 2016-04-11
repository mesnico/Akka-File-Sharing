package GUI;

import GUI.messages.SendModifyRequest;
import FileTransfer.FileModifier;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class FXMLMainController implements Initializable {
    @FXML
    private Label label;
    @FXML
    private TextField search;
    @FXML
    private TableView<FileEntry> table;
    
    @FXML
    private void modify(ActionEvent event) {
        System.out.println("You clicked Modify!");
        
        /* verify if the file is aviable
        FileEntry row = table.getSelectionModel().getSelectedItem();
        GuiActor.getClusterListenerActorRef().tell(new SendModifyRequest(row.getName(), row.getOwner(), FileModifier.WRITE), GuiActor.getGuiActorRef());
        */
        
        //this has to be done in another message...
        //createStage("Modify",true);
    }
    
    @FXML
    private void read(ActionEvent event) {
        System.out.println("You clicked Read!");
        /* verify if the file is aviable
        FileEntry row = table.getSelectionModel().getSelectedItem();
        GuiActor.getClusterListenerActorRef().tell(new SendModifyRequest(row.getName(), row.getOwner(), FileModifier.READ), GuiActor.getGuiActorRef());
        */
        
        //receive it if not busy
    }
    
    @FXML
    private void create(ActionEvent event) {
        System.out.println("You clicked Create new file!");
        
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Create.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Create");
            
            GUI.setSecondaryStage(stage);
            GUI.getSecondaryStage().show();
            GUI.getStage().hide();
            
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
            System.out.println(ex.getCause().toString());
        }
        
        GUI.getSecondaryStage().setOnCloseRequest((final WindowEvent windowEvent) -> { GUI.getStage().show(); });
    }
    
    @FXML
    private void search(ActionEvent event) {
        String searchText = search.getText();
        label.setText(searchText);
        if(!searchText.isEmpty()){
            //require the file
            /*
            SearchFile message = new SearchFile(filter(search.getText()));//filter is a function that help to check the correctness of the search
            GuiActor.controllerActorRef.tell(message, GuiActor.guiActorRef);
            */
            //another message will handle the generation of the raws in the TableView
        }
        else{
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Void search");
            alert.setContentText("You have to insert some text in the search box befor start to search!");

            alert.showAndWait();
        }
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
