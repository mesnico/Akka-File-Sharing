package GUI;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class FXMLMainController implements Initializable {

    @FXML
    private Label label;
    
    @FXML
    private TableView<String> table;
    
    @FXML
    private void Modify(ActionEvent event) {
        System.out.println("You clicked Modify!");
        label.setText("Modify!");
    }
    @FXML
    private void Create(ActionEvent event) {
        
    }
    
    static void addEntry(int message) {
        //Add the message to the TreeTableView
        
    }
    

    /*@FXML
    private void handleButtonAction(ActionEvent event) throws IOException{
        GuiActor.controllerActorRef.tell(new GuiCommandMessage(), GuiActor.guiActorRef);
        /*try{
            Thread.sleep(70000);
        }catch(InterruptedException e){}*//*
        Stage stage = new Stage();
        Parent root;
        
        //load up OTHER FXML document
        root = FXMLLoader.load(getClass().getResource("/fxml/Scene2.fxml"));
        
        //create a new scene with root and set the stage
         Scene scene = new Scene(root);
         stage.setScene(scene);
         stage.show();
       
    }*/
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
