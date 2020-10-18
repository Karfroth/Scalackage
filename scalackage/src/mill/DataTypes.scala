package com.karfroth.scalackage.mill

sealed trait DataTypes
final case class ScalaVersion(version: String) extends DataTypes
final case class ScalaJSVersion(version: String) extends DataTypes
final case class Dependency(groupId: String, artifactId: String, version: String) extends DataTypes

object DataTypes {
    def toMillSyntax(data: DataTypes): String = data match {
        case ScalaVersion(v) => s"""def scalaVersion = "${v}"""""
        case ScalaJSVersion(v) => s"""def scalaJSVersion = "${v}""""
        case Dependency(groupId, artifactId, version) => "" // s"""ivy"${groupId}::${artifactId}:${version}""""
    }
}