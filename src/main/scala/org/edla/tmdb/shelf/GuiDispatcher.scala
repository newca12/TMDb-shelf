package org.edla.tmdb.shelf

//https://gist.github.com/mucaho/8973013
// original work copyright 2012 Viktor Klang

/**
 * (A). define the gui dispatchers programmaticaly
 */

import akka.dispatch.{ DispatcherPrerequisites, ExecutorServiceFactory, ExecutorServiceConfigurator }
import com.typesafe.config.Config
import java.util.concurrent.{ ExecutorService, AbstractExecutorService, ThreadFactory, TimeUnit }
import java.util.Collections
import javafx.application.Platform

// First we wrap invokeLater/runLater as an ExecutorService
abstract class GUIExecutorService extends AbstractExecutorService {
  def execute(command: Runnable): Unit

  def shutdown(): Unit = ()

  def shutdownNow(): java.util.List[Runnable] = Collections.emptyList[Runnable]

  def isShutdown: Boolean = false

  def isTerminated: Boolean = false

  def awaitTermination(l: Long, timeUnit: TimeUnit): Boolean = true
}

object JavaFXExecutorService extends GUIExecutorService {
  override def execute(command: Runnable): Unit = Platform.runLater(command)
}

// Then we create an ExecutorServiceConfigurator so that Akka can use our JavaFXExecutorService for the dispatchers
class JavaFXEventThreadExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
    extends ExecutorServiceConfigurator(config, prerequisites) {
  private val f = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = JavaFXExecutorService
  }

  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = f
}
