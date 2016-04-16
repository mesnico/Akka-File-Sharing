package GUI;

import akka.actor.PoisonPill;
import java.util.List;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;


public class GUI extends Application {
    //this structure is needed to store name and tags of the file under modify (creazione)
    static public class ModifiedFile{
        private static String fileName;
        private static List<String> tags;

        public static void set(String fileName, List<String> tags) {
            ModifiedFile.fileName = fileName;
            ModifiedFile.tags = tags;
        }
        public static String getName(){
            return ModifiedFile.fileName;
        }
        public static List<String> getTags(){
            return ModifiedFile.tags;
        }
    }
    
    private static Stage primaryStage;
    private static Stage secondaryStage;
    
    public static void setSecondaryStage(Stage secondaryStage) {
        GUI.secondaryStage = secondaryStage;
    }

    public static Stage getSecondaryStage() {
        return secondaryStage;
    }
    
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
