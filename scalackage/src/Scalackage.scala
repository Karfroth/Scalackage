package com.karfroth.scalackage

import scala.util.Try
import scala.io.Source

object Scalackage {
    def main(args: Array[String]): Unit = {
        for {
            packageJson <- Try(Source.fromFile("package.json").getLines().mkString("\n")).toOption
            buildData <- BuildData.fromJson(packageJson)
        } yield (println(buildData))
    }
}