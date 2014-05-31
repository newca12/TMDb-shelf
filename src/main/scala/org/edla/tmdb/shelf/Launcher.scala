package org.edla.tmdb.shelf

import java.util.prefs.Preferences
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.controlsfx.dialog.Dialog
import org.controlsfx.dialog.Dialogs
import org.edla.tmdb.client.InvalidApiKeyException
import javafx.event.EventHandler
import javafx.stage.WindowEvent
import javafx.scene.Scene

import akka.actor.ActorSystem
import akka.actor.Props

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

  def main(args: Array[String]) {
    new Launcher().launch
  }

  import java.nio.file.{ Paths, Files }
  val home = System.getProperty("user.home")
  val localStore = s"${home}/.tmdb-shelf"
  Files.createDirectories(Paths.get(localStore))

  val system = ActorSystem("ShelfSystem")
  val scalaFxActor = system.actorOf(Props[ScalaFxActor].withDispatcher("javafx-dispatcher"), "ScalaFxActor")

}

class Launcher extends javafx.application.Application /*with WithUncaughtExceptionHandlerDialog*/ {

  def launch = javafx.application.Application.launch()

  import java.io.IOException
  val resource = getClass.getResource("view/Shelf.fxml")
  if (resource == null) {
    throw new IOException("Cannot load resource: view/Shelf.fxml")
  }

  import javafx.{ fxml ⇒ jfxf }
  import javafx.{ scene ⇒ jfxs }
  val root: jfxs.Parent = jfxf.FXMLLoader.load(resource)

  import javafx.stage.Stage
  override def start(primaryStage: Stage) {
    val scene = new Scene(root)
    primaryStage.setTitle("TMDb-shelf")
    primaryStage.setScene(scene)
    primaryStage.show()
    val apiKey = checkOrAskApiKey(primaryStage)
    validateApiKey(apiKey)

    primaryStage.setOnCloseRequest(new EventHandler[WindowEvent] {
      def handle(p1: WindowEvent) = System.exit(0)
    })
  }

  def checkOrAskApiKey(primaryStage: Stage) = {
    val prefs = Preferences.userRoot().node(this.getClass().getName())
    val apiKey = prefs.get("apiKey", "")
    if (apiKey.isEmpty()) {
      val response = Dialogs.create()
        .owner(primaryStage)
        .title("Information needed")
        .message("Enter your API key")
        .showTextInput()
      if (response.isPresent()) response.get()
      else sys.exit
    } else apiKey
  }

  def validateApiKey(apiKey: String) {
    val prefs = Preferences.userRoot().node(this.getClass().getName())
    //uncomment to clean store
    //prefs.remove("apiKey")
    val apiKeyStored = prefs.get("apiKey", "")
    //http://tersesystems.com/2012/12/27/error-handling-in-scala/
    //http://mauricio.github.io/2014/02/17/scala-either-try-and-the-m-word.html
    val shelfActor = Launcher.system.actorOf(Props(ShelfActor(apiKey, 45 second)), name = "shelfactor")
    val tmdbClient = Utils.getTmdbClient
    val token = Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    token match {
      case Success(v) ⇒
        //comment to clean store
        prefs.put("apiKey", apiKey)
        Dialogs.create()
          .owner(null)
          .title("Information")
          .masthead(null)
          .message("Perfect ! Your API key is valid")
          .showInformation()
      case Failure(e) ⇒
        //if (e.isInstanceOf[InvalidApiKeyException]) {
        if (!apiKeyStored.isEmpty() && apiKeyStored == apiKey) prefs.remove("apiKey")
        val response = Dialogs.create()
          .title("Alert")
          .message(e.getMessage())
          .showError()
        if (response == Dialog.Actions.OK) sys.exit
      //} else throw (e)
    }
  }

}
