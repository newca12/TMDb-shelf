package org.edla.tmdb

package object shelf {

  import acyclic.pkg

  val home = System.getProperty("user.home")
  val localStore = s"${home}/.tmdb-shelf"

}

