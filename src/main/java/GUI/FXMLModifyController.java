package GUI;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

public class FXMLModifyController implements Initializable {
    
    @FXML
    private void done(ActionEvent event) {
       GUI.getSecondaryStage().close();
       
       //the file under modify is: GUI.ModifiedFile.getName();
       
       //the file exits the busy-state
       //load distribution (only destination election)
       //update tags
       //send file
       
       GUI.getStage().show();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
