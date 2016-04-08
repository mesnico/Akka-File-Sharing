package GUI;

import akka.actor.PoisonPill;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;


public class GUI extends Application {
    private static Stage primaryStage;
    
    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Main.fxml"));
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add("/styles/Styles.css");
        
        stage.setTitle("JavaFX and Maven");
        stage.setScene(scene);
        stage.show();
        
        //at the event "close window"
        stage.setOnCloseRequest((WindowEvent we) -> {
            //tell the cluster system to initiate the shutdown
            GuiActor.getClusterListenerActorRef().tell(new ClusterListenerActor.messages.InitiateShutdown(), GuiActor.getGuiActorRef());
            
            //send Poison Pill to me to kill myself
            GuiActor.getGuiActorRef().tell(PoisonPill.getInstance(), GuiActor.getGuiActorRef());
        });
        
    }
    
    public static Stage getStage(){
        return primaryStage;
    }
}
