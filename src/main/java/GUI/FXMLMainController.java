package GUI;

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
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class FXMLMainController implements Initializable {
    static private Stage secondaryStage;
    
    @FXML
    private Label label;
    @FXML
    private TextField search;
    @FXML
    private TableView<FileEntry> table;
    
    public static void setSecondaryStage(Stage secondaryStage) {
        FXMLMainController.secondaryStage = secondaryStage;
    }

    public static Stage getSecondaryStage() {
        return secondaryStage;
    }
    
    private void createStage(String FXMLFileName, boolean disableButtons){
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/"+FXMLFileName+".fxml"));
            Parent root = (Parent) fxmlLoader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle(FXMLFileName);
            if(disableButtons){
                //remove "minimize" and "restore down" buttons
                stage.initStyle(StageStyle.UTILITY);
                //disable close button
                stage.setOnCloseRequest((final WindowEvent windowEvent) -> { windowEvent.consume(); });
            }
            setSecondaryStage(stage);
            getSecondaryStage().show();
            GUI.getStage().hide();
            //((Stage) label.getScene().getWindow()).hide();
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
            System.out.println(ex.getCause().toString());
        }
    }
    
    @FXML
    private void modify(ActionEvent event) {
        System.out.println("You clicked Modify!");
        //require the file
        /*
        //get the selected entry of the TableView
        FileEntry row = table.getSelectionModel().getSelectedItem();
        //create a request of the file selected
        ReadFile message = new ReadFile(row);//filter is a function that help to check the correctness of the search
        //send the request to the clusterListener
        GuiActor.controllerActorRef.tell(message, GuiActor.guiActorRef);
        */
        //receive it if not busy
        
        //this has to be done in another message...
        createStage("Modify",true);
    }
    
    @FXML
    private void create(ActionEvent event) {
        System.out.println("You clicked Create new file!");
        //label.setText("New file");
        
        createStage("Create", false);
    }
    
    @FXML
    private void read(ActionEvent event) {
        FileEntry row = table.getSelectionModel().getSelectedItem();
        //require the file
        /*
        ReadFile message = new ReadFile(row);//filter is a function that help to check the correctness of the search
        GuiActor.controllerActorRef.tell(message, GuiActor.guiActorRef);
        */
        //receive it if not busy
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
