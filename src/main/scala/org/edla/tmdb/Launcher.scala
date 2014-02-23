package org.edla.tmdb

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
import org.edla.tmdb.client.TmdbClient

import javafx.event.EventHandler
import javafx.stage.WindowEvent
import scalafx.Includes.jfxParent2sfx
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.TextField
import scalafx.stage.Stage.sfxStage2jfx
import scalafxml.core.FXMLView
import scalafxml.core.NoDependencyResolver

import scalafxml.core.macros.sfxml

@sfxml
class TmdbPresenter(private val apikey: TextField) {

  def check(event: ActionEvent) {

  }

  def handleClear(event: ActionEvent) {
    apikey.text = ""
  }

}

//inspired by typesafe migration-manager
trait WithUncaughtExceptionHandlerDialog {
  /** Show a dialog for all uncaught exception. */
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

  /** Default exception handler */
  protected val handler: Thread.UncaughtExceptionHandler = new UncaughtExceptionHandlerDialog()

  // setting the handler (assumes it is set only once, here - no way to enforce this though)
  Thread.setDefaultUncaughtExceptionHandler(handler)
}

object Launcher extends JFXApp with WithUncaughtExceptionHandlerDialog {

  val root = FXMLView(getClass.getResource("view/Shelf.fxml"), NoDependencyResolver)

  stage = new PrimaryStage() {
    title = "TMDb-shelf"
    scene = new Scene(root)
    val apiKey = checkOrAskApiKey()
    validateApiKey(apiKey)
  }
  stage.setOnCloseRequest(new EventHandler[WindowEvent] {
    def handle(p1: WindowEvent) = System.exit(0)
  })

  def checkOrAskApiKey(): String = {
    val prefs = Preferences.userRoot().node(this.getClass().getName())
    val apiKey = prefs.get("apiKey", "")
    if (apiKey.isEmpty()) {
      val response = Dialogs.create()
        .owner(stage)
        .title("Information needed")
        .message("Enter your API key")
        .showTextInput()
      if (response == null) sys.exit
      response
    } else apiKey
  }

  def validateApiKey(apiKey: String) {
    val prefs = Preferences.userRoot().node(this.getClass().getName())
    //prefs.remove("apiKey")
    val apiKeyStored = prefs.get("apiKey", "")
    val tmdbClient = TmdbClient(apiKey)
    //http://tersesystems.com/2012/12/27/error-handling-in-scala/
    //http://mauricio.github.io/2014/02/17/scala-either-try-and-the-m-word.html
    val token = Try(Await.result(tmdbClient.getToken, 2 seconds).request_token)
    token match {
      case Success(v) ⇒
        prefs.put("apiKey", apiKey)
        Dialogs.create()
          .owner(null)
          .title("Information")
          .masthead(null)
          .message("Perfect ! Your API key is valid")
          .showInformation()
      case Failure(e) ⇒
        if (e.isInstanceOf[InvalidApiKeyException]) {
          if (!apiKeyStored.isEmpty() && apiKeyStored == apiKey) prefs.remove("apiKey")
          val response = Dialogs.create()
            .title("Alert")
            .message(e.getMessage())
            .showError()
          if (response == Dialog.Actions.OK) sys.exit
          //response
        } else throw (e)
    }
  }

}
