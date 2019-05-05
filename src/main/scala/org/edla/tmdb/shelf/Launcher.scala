package org.edla.tmdb.shelf

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.util.prefs.Preferences

import akka.actor.{ActorSystem, Props}
import javafx.application.Application
import javafx.application.Application._
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.{Alert, TextInputDialog}
import javafx.stage.{Modality, Stage, WindowEvent}
import javafx.{fxml => jfxf, scene => jfxs}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/*//inspired by typesafe migration-manager
trait WithUncaughtExceptionHandlerDialog {

 */
/** Show a dialog for all uncaught exception. */ /*
  private class UncaughtExceptionHandlerDialog extends Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable) {
      try {
        val response = Dialogs.create()
          .title("An unexpected error occurred. Please create a ticket with a copy of the stack trace")
          .showException(e)
        if (response == Dialog.Actions.OK) sys.exit
      } catch {
        // Being extremely defensive (avoid running into infinite loop)
        case t: Throwable ⇒ t.printStackTrace()
      }
    }
  }

 */
/** Default exception handler */ /*
  protected val handler: Thread.UncaughtExceptionHandler = new UncaughtExceptionHandlerDialog()

  // setting the handler (assumes it is set only once, here - no way to enforce this though)
  Thread.setDefaultUncaughtExceptionHandler(handler)
}*/

object Launcher {

  val tmpDir = System.getProperty("java.io.tmpdir")
  val system = ActorSystem("ShelfSystem")
  Files.createDirectories(Paths.get(localStore))
  @volatile lazy val scalaFxActor =
    system.actorOf(Props[ScalaFxActor].withDispatcher("javafx-dispatcher"), "ScalaFxActor")
  var stage: Stage = _

}

class Launcher extends Application /*with WithUncaughtExceptionHandlerDialog*/ {

  def main(args: Array[String]): Unit = {
    launch(args: _*)
  }

  val resource = Option(getClass.getResource("view/Shelf.fxml"))
  if (resource.isEmpty) {
    throw new IOException("Cannot load resource: view/Shelf.fxml")
  }
  lazy val root: jfxs.Parent = jfxf.FXMLLoader.load(resource.get)

  override def start(primaryStage: Stage): Unit = {
    Launcher.stage = primaryStage
    val scene = new Scene(root)
    primaryStage.setTitle(s"${BuildInfo.name} ${BuildInfo.version}")
    primaryStage.setScene(scene)
    primaryStage.show()
    val apiKey = checkOrAskApiKey(primaryStage)
    validateApiKey(apiKey)

    primaryStage.setOnCloseRequest(new EventHandler[WindowEvent] {
      def handle(p1: WindowEvent) = System.exit(0)
    })
  }

  private def checkOrAskApiKey(primaryStage: Stage) = {
    val prefs  = Preferences.userRoot().node(this.getClass.getName)
    val apiKey = prefs.get("apiKey", "")
    if (apiKey.isEmpty) {
      val dialog = new TextInputDialog() {
        initOwner(Launcher.stage)
        initModality(Modality.APPLICATION_MODAL)
        setTitle("Information needed")
        // scalastyle:off null
        setHeaderText(null)
        // scalastyle:on null
        setContentText("Enter your API key")
      }
      val response = dialog.showAndWait()
      if (response.isPresent) {
        response.get().trim()
      } else {
        sys.exit
      }
    } else {
      apiKey
    }
  }

  private def validateApiKey(apiKey: String) = {
    val prefs = Preferences.userRoot().node(this.getClass.getName)
    //uncomment to clean store
    //prefs.remove("apiKey")
    val apiKeyStored = prefs.get("apiKey", "")
    //http://tersesystems.com/2012/12/27/error-handling-in-scala/
    //http://mauricio.github.io/2014/02/17/scala-either-try-and-the-m-word.html
    Launcher.system.actorOf(ShelfActor.props(apiKey, 45 second), name = "shelfactor")
    val tmdbClient = Utils.getTmdbClient
    val token      = Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    token match {
      case Success(v) =>
        //comment to clean store
        prefs.put("apiKey", apiKey)
        new TextInputDialog()
        new Alert(AlertType.INFORMATION) {
          initOwner(Launcher.stage)
          initModality(Modality.APPLICATION_MODAL)
          setTitle("Information")
          // scalastyle:off null
          setHeaderText(null)
          // scalastyle:on null
          setContentText("Perfect ! Your API key is valid")
          showAndWait()
        }
      case Failure(e) =>
        //if (e.isInstanceOf[InvalidApiKeyException]) {
        if (!apiKeyStored.isEmpty && apiKeyStored == apiKey) {
          prefs.remove("apiKey")
        }
        new Alert(AlertType.INFORMATION) {
          initOwner(Launcher.stage)
          initModality(Modality.APPLICATION_MODAL)
          setTitle("Alert")
          // scalastyle:off null
          setHeaderText(null)
          // scalastyle:on null
          setContentText("(e.getMessage()")
        }
        sys.exit
      //} else throw (e))))
    }
  }

}
