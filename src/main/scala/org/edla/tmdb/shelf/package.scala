package org.edla.tmdb

package object shelf {

  val home       = System.getProperty("user.home")
  val localStore = s"$home/.tmdb-shelf"
}
