package org.edla.tmdb.shelf

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import java.util.prefs.Preferences
import akka.actor.{ActorSystem, Props}
import javafx.application.Application
import javafx.application.Application._
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control.{Alert, ButtonType, CheckBox, Dialog, Label, TextField, TextInputDialog}
import javafx.scene.layout.GridPane
import javafx.stage.{Modality, Stage, WindowEvent}
import javafx.util.Callback
import javafx.{fxml => jfxf, scene => jfxs}
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.edla.tmdb.client.TmdbClient
import org.edla.tmdb.shelf.Launcher.{Config, dbName, localStore}

import java.util.Optional
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
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

  case class Config(apiKey: String, login: String, password: String, host: String, remoteMode: Boolean)
  val tmpDir     = System.getProperty("java.io.tmpdir")
  val system     = ActorSystem("ShelfSystem")
  val home       = System.getProperty("user.home")
  var localStore = s"$home${File.separator}.tmdb-shelf"
  var dbName     = "tmdb-shelf"

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
    val config = checkOrAskApiKey(primaryStage)
    validateSSH(config)
    validateApiKey(config)

    primaryStage.setOnCloseRequest(new EventHandler[WindowEvent] {
      def handle(p1: WindowEvent) = System.exit(0)
    })
  }

  private def checkOrAskApiKey(primaryStage: Stage) = {

    val prefs      = Preferences.userRoot().node(this.getClass.getName)
    val remoteMode = if (prefs.get("remote", "true") == "true") true else false
    val confLocked = prefs.get("locked", "")
    val config = Config(
      prefs.get("apiKey", ""),
      prefs.get("login", ""),
      prefs.get("password", ""),
      prefs.get("host", ""),
      remoteMode
    )

    if (confLocked.isEmpty || config.apiKey.isEmpty || config.login.isEmpty || config.password.isEmpty || config.host.isEmpty) {
      val dialog: Dialog[Config] = new Dialog[Config]() {
        initOwner(Launcher.stage)
        initModality(Modality.APPLICATION_MODAL)
        setTitle("Information needed")
      }
      val labelApiKey     = new Label("API Key: ")
      val apiKey          = new TextField(config.apiKey)
      val labelLogin      = new Label("Login: ")
      val login           = new TextField(config.login)
      val labelPassword   = new Label("Password: ")
      val password        = new TextField(config.password)
      val labelHost       = new Label("Host: ")
      val host            = new TextField(config.host)
      val labelRemoteMode = new Label("Remote mode: ")
      val remoteMode      = new CheckBox()
      remoteMode.setSelected(config.remoteMode)
      val grid = new GridPane()
      grid.add(labelApiKey, 1, 1)
      grid.add(apiKey, 2, 1)
      grid.add(labelLogin, 1, 2)
      grid.add(login, 2, 2)
      grid.add(labelPassword, 1, 3)
      grid.add(password, 2, 3)
      grid.add(labelHost, 1, 4)
      grid.add(host, 2, 4)
      grid.add(labelRemoteMode, 1, 5)
      grid.add(remoteMode, 2, 5)
      dialog.getDialogPane().setContent(grid)
      val buttonTypeOk = new ButtonType("Okay", ButtonData.OK_DONE)
      dialog.getDialogPane().getButtonTypes().add(buttonTypeOk)
      dialog.setResultConverter(new Callback[ButtonType, Config]() {
        override def call(b: ButtonType) = {
          if (b == buttonTypeOk)
            new Config(apiKey.getText, login.getText, password.getText, host.getText, remoteMode.isSelected)
          else null
        }
      })
      val response: Optional[Config] = dialog.showAndWait()
      if (response.isPresent) {
        response.get()
      } else {
        sys.exit
      }
    } else {
      config
    }
  }

  private def validateApiKey(config: Config) = {
    val prefs = Preferences.userRoot().node(this.getClass.getName)
    //uncomment to clean store
    //prefs.remove("apiKey")
    val apiKeyStored = prefs.get("apiKey", "")
    //http://tersesystems.com/2012/12/27/error-handling-in-scala/
    //http://mauricio.github.io/2014/02/17/scala-either-try-and-the-m-word.html
    Launcher.system.actorOf(ShelfActor.props(config, 45 second), name = "shelfactor")
    val tmdbClient = Utils.getTmdbClient
    val token      = Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    token match {
      case Success(v) =>
        //comment to clean store
        prefs.put("apiKey", config.apiKey)
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
        if (!apiKeyStored.isEmpty && apiKeyStored == config.apiKey) {
          prefs.remove("apiKey")
        }
        new Alert(AlertType.INFORMATION) {
          initOwner(Launcher.stage)
          initModality(Modality.APPLICATION_MODAL)
          setTitle("Alert")
          // scalastyle:off null
          setHeaderText(null)
          // scalastyle:on null
          setContentText(s"(${e.getMessage()}")
          showAndWait()
        }
        sys.exit
      //} else throw (e))))
    }
  }

  private def validateSSH(config: Config) = {
    val prefs             = Preferences.userRoot().node(this.getClass.getName)
    val client: SSHClient = new SSHClient()
    client.addHostKeyVerifier(new PromiscuousVerifier())
    client.connect(config.host)
    client.authPassword(config.login, config.password)
    val download = Try { Sync.download(client) }

    download match {
      case Success(lastSeenMovies) =>
        val tmdbClient = TmdbClient(config.apiKey, java.util.Locale.getDefault().getLanguage, 45.seconds)
        if (config.remoteMode == true) {
          localStore = s"$localStore-remote"
          dbName = s"$dbName-remote"
          for (m <- lastSeenMovies) {
            tmdbClient.getMovie(m.tmdbId).map { movie =>
              val filename = Paths.get(s"${Launcher.localStore}${File.separator}${m.tmdbId}.jpg")
              if (!Files.exists(filename)) {
                tmdbClient.downloadPoster(movie, filename)
              }
            }
            DAO.insert(m)
          }
        }
        Files.createDirectories(Paths.get(localStore))
        prefs.put("login", config.login)
        prefs.put("password", config.password)
        prefs.put("host", config.host)
        prefs.put("locked", "true")
        new TextInputDialog()
        new Alert(AlertType.INFORMATION) {
          initOwner(Launcher.stage)
          initModality(Modality.APPLICATION_MODAL)
          setTitle("Information")
          // scalastyle:off null
          setHeaderText(null)
          // scalastyle:on null
          setContentText("Perfect ! Your configuration is valid")
          showAndWait()
        }
      case Failure(e) =>
        //if (e.isInstanceOf[InvalidApiKeyException]) {
        //if (!apiKeyStored.isEmpty && apiKeyStored == apiKey) {
        //  prefs.remove("apiKey")
        //}
        new Alert(AlertType.INFORMATION) {
          initOwner(Launcher.stage)
          initModality(Modality.APPLICATION_MODAL)
          setTitle("Alert")
          // scalastyle:off null
          setHeaderText(null)
          // scalastyle:on null
          setContentText(s"(${e.getMessage()}")
          showAndWait()
        }
        sys.exit
      //} else throw (e))))
    }
  }

}
