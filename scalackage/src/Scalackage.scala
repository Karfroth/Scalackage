package com.karfroth.scalackage

import scala.util.Try
import scala.io.Source

import com.karfroth.scalackage.mill.MillBuildData
import com.karfroth.scalackage.converter.TSConverter

object Scalackage {
    def main(args: Array[String]): Unit = {
        TSConverter.build(TSConverter.DefaultOptions) match {
            case Left(f) => TSConverter.handleFailure(f)
            case Right(dependencies) => {
                for {
                    packageJson <- Try(Source.fromFile("package.json").getLines().mkString("\n")).toOption
                    buildData <- MillBuildData.fromJson(packageJson)
                } yield (println(buildData.copy(dependencies = dependencies)))
            }
        }
    }
}