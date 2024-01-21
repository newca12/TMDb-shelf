package org.edla.tmdb.shelf

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers

class ImdbInfoTest extends AnyPropSpec with Matchers {
  property("Get correct ratings and detect theatrical films") {
    ImdbInfo.getInfo("tt1390411") shouldBe ((Some(6.9), Some(false)))
    ImdbInfo.getInfo("tt0304584") shouldBe ((Some(4.2), Some(true)))
    ImdbInfo.getInfo("tt0827521") shouldBe ((Some(5.5), Some(true)))
    ImdbInfo.getInfo("tt0001539") shouldBe ((None, Some(false)))
    ImdbInfo.getInfo("tt5031232") shouldBe ((Some(8.6), Some(true)))
    ImdbInfo.getInfo("tt4049416") shouldBe ((Some(5.2), Some(true)))
    //TODO: manage redirection
    //ImdbInfo.getInfo("tt17061910") shouldBe ((Some(8.0), Some(true)))
  }
}
