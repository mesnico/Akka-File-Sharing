/*reference:
https://bitbucket.org/mucaho/scalatrix/src/b5c3b0b77d3a?at=master */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akka.dispatch.gui;

import akka.dispatch.DispatcherPrerequisites;
import akka.dispatch.ExecutorServiceConfigurator;
import akka.dispatch.ExecutorServiceFactory;
import com.typesafe.config.Config;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/**
 *
 * @author Nicola
 */
    // First we wrap invokeLater as an ExecutorService
abstract class GUIExecutorService extends AbstractExecutorService {
    /*def execute(command: Runnable): Unit
     def shutdown(): Unit = ()
     def shutdownNow() = Collections.emptyList[Runnable]
     def isShutdown = false
     def isTerminated = false
     def awaitTermination(l: Long, timeUnit: TimeUnit) = true*/

    @Override
    abstract public void execute(Runnable command);

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

}

class JavaFXExecutorService extends GUIExecutorService {
    //override def execute(command: Runnable) = Platform.runLater(command)
    @Override
    public void execute(Runnable command) {
        Platform.runLater(command);
    }
}
    
// We create an ExecutorServiceConfigurator so that Akka can use our JavaFXExecutorService for the dispatchers
/*class JavaFXEventThreadExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {
  private val f = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = JavaFXExecutorService
  }

  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = f
}*/
class JavaFXEventThreadExecutorServiceConfigurator extends ExecutorServiceConfigurator{
    final private JavaFXExecutorService jfx = new JavaFXExecutorService();
    
    public JavaFXEventThreadExecutorServiceConfigurator(Config config, DispatcherPrerequisites prerequisites){
        super(config,prerequisites);
    }
    
    private ExecutorServiceFactory f = new ExecutorServiceFactory(){
        @Override
        public ExecutorService createExecutorService(){
            return jfx;
        }
    };
    
    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(String id, ThreadFactory tf){
        return f;
    }
        
}
    
    


