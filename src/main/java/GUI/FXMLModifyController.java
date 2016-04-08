package GUI;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;

public class FXMLModifyController implements Initializable {

    @FXML
    private Button done;
    
    @FXML
    private void done(ActionEvent event) {
       FXMLMainController.getSecondaryStage().close();
       GUI.getStage().show();
       
       //
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
