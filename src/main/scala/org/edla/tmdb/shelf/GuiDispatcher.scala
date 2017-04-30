package org.edla.tmdb.shelf

//https://gist.github.com/mucaho/8973013
// original work copyright 2012 Viktor Klang

/**
  * (A). define the gui dispatchers programmaticaly
  */
import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, ExecutorService, ThreadFactory, TimeUnit}
import javafx.application.Platform

import akka.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceFactory}
import com.typesafe.config.Config

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
