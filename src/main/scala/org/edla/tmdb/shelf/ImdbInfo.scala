package org.edla.tmdb.shelf

import org.htmlcleaner.{CleanerProperties, DomSerializer, HtmlCleaner, TagNode}

import java.net.URL
import javax.xml.xpath.{XPath, XPathConstants, XPathFactory}
import io.{BufferedSource, Source}
import java.net.URL

object ImdbInfo extends {
  def getInfo(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    val url = new URL(s"https://www.imdb.com/title/$imdbId/")
    val requestProperties = Map(
      "User-Agent" -> "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:107.0) Gecko/20100101 Firefox/107.0"
    )
    val connection = new URL(s"https://www.imdb.com/title/$imdbId/").openConnection
    requestProperties.foreach({
      case (name, value) => connection.setRequestProperty(name, value)
    })

    val is               = Source.fromInputStream(connection.getInputStream).reader() //.getLines.mkString("\n"))
    val cleaner          = new HtmlCleaner()
    val tagNode: TagNode = cleaner.clean(is)
    //println("<" + tagNode.getName + ">" + cleaner.getInnerHtml(tagNode) + "</" + tagNode.getName + ">")
    val doc: org.w3c.dom.Document = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val xpath: XPath              = XPathFactory.newInstance().newXPath()
    val (rawScore, rawIsNotTheatricalFilm) = {
      newSite(xpath, doc) match {
        case (a, b) if (a.isEmpty && b.isEmpty) => oldSite(xpath, doc)
        case (a, b)                             => (a, b)
      }
    }
    //println(rawIsNotTheatricalFilm)
    val score =
      if (rawScore.isEmpty) {
        None
      } else {
        Some(BigDecimal(rawScore.split("/").head))
      }
    val isNotTheatricalFilm = Some(
      List("TV Movie", "TV Short", "Video", "Episode aired", "TV Series", "TV Special").exists {
        rawIsNotTheatricalFilm.contains
      }
    )
    (score, isNotTheatricalFilm)
  }

  def oldSite(xpath: XPath, doc: org.w3c.dom.Document): (String, String) = {
    (
      xpath
        .evaluate(
          "//span[@class='sc-e457ee34-1 gvYTvP']",
          doc,
          XPathConstants.STRING
        )
        .toString,
      xpath
        .evaluate(
          "//ul[@class='ipc-inline-list ipc-inline-list--show-dividers sc-f26752fb-0 iQHuAC baseAlt']",
          doc,
          XPathConstants.STRING
        )
        .toString
    )
  }

  def newSite(xpath: XPath, doc: org.w3c.dom.Document): (String, String) = {
    (
      xpath
        .evaluate(
          "//span[@class='sc-e457ee34-1 squoh']",
          doc,
          XPathConstants.STRING
        )
        .toString,
      xpath
        .evaluate(
          "//ul[@class='ipc-inline-list ipc-inline-list--show-dividers sc-afe43def-4 kdXikI baseAlt']",
          doc,
          XPathConstants.STRING
        )
        .toString
    )
  }

  def getScoreFromId(imdbId: String): Option[BigDecimal] = {
    if (imdbId.isEmpty) {
      None
    } else {
      getInfo(imdbId)._1
    }
  }

  def getInfoFromId(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    if (imdbId.isEmpty) {
      (None, None)
    } else {
      getInfo(imdbId)
    }
  }
}
