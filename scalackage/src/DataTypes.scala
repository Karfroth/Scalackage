package com.karfroth.scalackage

sealed trait DataTypes
final case class ScalaVersion(version: String) extends DataTypes
final case class ScalaJSVersion(version: String) extends DataTypes
final case class Dependency(groupId: String, artifactId: String, version: String) extends DataTypes

final case class BuildData(
    scalaVersion: ScalaVersion
,   scalaJSVersion: ScalaJSVersion
,   dependencies: Seq[Dependency]
)

object BuildData {
    def fromJson(input: String): Option[BuildData] = {
        val json = ujson.read(input)
        val buildDataJson = json("scalackage").objOpt
        buildDataJson.map{ data => 
            BuildData(
                scalaVersion   = ScalaVersion(data.get("scalaVersion").map(_.str).getOrElse("2.13.3"))
            ,   scalaJSVersion = ScalaJSVersion(data.get("scalaJSVersion").map(_.str).getOrElse("1.2.0"))
            ,   dependencies   = Seq()
            )
        }
    }
}