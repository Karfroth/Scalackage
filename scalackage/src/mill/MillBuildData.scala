package com.karfroth.scalackage.mill

final case class MillBuildData(
    scalaVersion: ScalaVersion
,   scalaJSVersion: ScalaJSVersion
,   dependencies: Seq[Dependency]
)

object MillBuildData {
    def fromJson(input: String): Option[MillBuildData] = {
        val json = ujson.read(input)
        val buildDataJson = json("scalackage").objOpt
        buildDataJson.map{ data => 
            MillBuildData(
                scalaVersion   = ScalaVersion(data.get("scalaVersion").map(_.str).getOrElse("2.13.3"))
            ,   scalaJSVersion = ScalaJSVersion(data.get("scalaJSVersion").map(_.str).getOrElse("1.2.0"))
            ,   dependencies   = Seq()
            )
        }
    }
}