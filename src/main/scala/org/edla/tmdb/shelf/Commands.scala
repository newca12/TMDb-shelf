package org.edla.tmdb.shelf

import java.io.File

import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.model.ExtractedResult

import collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.async.Async.async
import scala.concurrent.ExecutionContext.Implicits.global

/* SPECS
 - get a file list of movie (viewable and not viewable)
 - get a file list from DB (viewable and not viewable)
 - compare to title
 - compare to original title
 - compare to comment
 - pick the best score
 */

object Commands /*extends App*/ {

  val movies: Future[Seq[MovieDB]] = DAO.filter(0, 0, "")
  val results: Seq[MovieDB]        = Await.result(movies, 5.seconds)
  println(results)

  def check() = {
    val filesTitles: List[(String, String)] = Source
      .fromFile("FQDN_PATH")
      .getLines
      .toList
      .map(fileTitle ⇒ (fileTitle, fileTitle.split("20")(0).toLowerCase())) //getListOfFiles("FQDN_PATH").map(_.getName)

    println(filesTitles)

    def getListOfFiles(dir: String): List[File] = {
      val d = new File(dir)
      if (d.exists && d.isDirectory) {
        d.listFiles.filter(_.isFile).toList
      } else {
        List[File]()
      }
    }

    def fuzz(title: String): ExtractedResult = {
      FuzzySearch.extractOne(title.toLowerCase(), filesTitles.map(t ⇒ t._2).asJava)
    }

    for (movie ← results) {
      val r1 = fuzz(movie.title)
      val r2 = fuzz(movie.originalTitle)
      val r3 = fuzz(movie.comment)
      val r: Option[ExtractedResult] =
        if (r1.getScore >= r2.getScore && r1.getScore >= r3.getScore) Some(r1)
        else if (r2.getScore >= r1.getScore && r2.getScore >= r3.getScore) Some(r2)
        else if (r3.getScore >= r1.getScore && r3.getScore >= r2.getScore) Some(r3)
        else None
      println(FuzzySearch.extractTop(movie.title, filesTitles.map(t ⇒ t._2).asJava, 5))
      val fullFileName: Unit = for (fileTitle ← filesTitles) {
        if (fileTitle._2 == r.get.getString) println(fileTitle._1)
      }
      println(r.get.getScore + " : " + movie.title + " ==> " + r.get.getString)
    }

    println(results.size + ":" + filesTitles.size)

  }

  def findChangedScore(shelf: TmdbPresenter) = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    async {
      for (movie ← results) {
        println(movie.title + ":" + movie.imdbScore)
        if (ImdbInfo.getScoreFromId(movie.imdbId) != movie.imdbScore)
          shelfActor ! Utils.FoundNewScore(shelf, movie.title)
      }
      shelfActor ! Utils.FindchangedScoreTerminated(shelf)
    }
  }

}
