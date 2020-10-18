package com.karfroth.scalackage

import scala.util.Try
import scala.io.Source

import com.karfroth.scalackage.mill.MillBuildData

object Scalackage {
    def main(args: Array[String]): Unit = {
        for {
            packageJson <- Option("{}")
            buildData <- MillBuildData.fromJson(packageJson)
        } yield (println(buildData))
    }
}