/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.TagSearchGuiResponse;
import Startup.AddressResolver;
import Startup.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 *
 * @author nicky
 */
public class GuiActor extends UntypedActor{
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private static ActorRef guiActorRef;
    private static ActorSelection clusterListenerActorRef,soulReaper;
    private final int clusterSystemPort;
    
    public GuiActor(int basePort){
        this.clusterSystemPort = basePort;
    }
    
    @Override 
    public void preStart() throws Exception{
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/clusterListener");
        soulReaper =              getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/soulReaper");
        
        //subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
    }
    
    @Override
    public void onReceive(Object message) throws Exception{
        if(message instanceof CreationResponse){
            log.info("GUI received creation response for file: success {}",((CreationResponse) message).isSuccess());
            if(((CreationResponse) message).isSuccess()){
                //from a request of creation I obtained positive response => Start che creation and modify of the new file
                GUI.getSecondaryStage().close();
                
                try {
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Modify.fxml"));
                    Parent root = (Parent) fxmlLoader.load();
                    Stage stage = new Stage();
                    stage.setScene(new Scene(root));
                    stage.setTitle("Modify");
                    //remove "minimize" and "restore down" buttons
                    stage.initStyle(StageStyle.UTILITY);
                    //disable close button
                    stage.setOnCloseRequest((final WindowEvent windowEvent) -> { windowEvent.consume(); });
                    
                    GUI.setSecondaryStage(stage);
                    GUI.getSecondaryStage().show();
                    GUI.getStage().hide();
                    
                } catch(Exception ex) {
                    System.out.println(ex.getMessage());
                    System.out.println(ex.getCause().toString());
                }
            }else{
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Bad name");
                alert.setContentText("This file is already present in the cluster!");

                alert.showAndWait();
            }
            
        } else if (message instanceof TagSearchGuiResponse){
            TagSearchGuiResponse r = (TagSearchGuiResponse)message;
            log.info("Received search infos: {}",r.getReturnedList());
            
            ObservableList<FileEntry> tags = FXCollections.observableList(r.getReturnedList());
            //for(FileEntry fe : r.getReturnedList()) tags.add(fe);
            log.info("Received search infos (ObservableList<FileEntry>): {}",tags);
            FXMLMainController.getTable().setItems(tags);
            FXMLMainController.getTable().sort();
        }
        
        /*
        if(message instanceof ModifyRequest){
            
        }*/    
    }

    public static ActorRef getGuiActorRef() {
        return guiActorRef;
    }

    public static ActorSelection getClusterListenerActorRef() {
        return clusterListenerActorRef;
    }
}
