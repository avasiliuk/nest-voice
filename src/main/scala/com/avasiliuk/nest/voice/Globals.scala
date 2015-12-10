package com.avasiliuk.nest.voice

import com.typesafe.config.ConfigFactory

/**
  * User: Aliaksandr Vasiliuk Date: 12/10/15
  */
object Globals {
  lazy val config = ConfigFactory.load()
}
