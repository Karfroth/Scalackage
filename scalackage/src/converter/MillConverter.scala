package com.karfroth.scalackage.converter

import com.karfroth.scalackage.DataTypes
import com.karfroth.scalackage.ScalaJSVersion
import com.karfroth.scalackage.ScalaVersion
import com.karfroth.scalackage.Dependency

object MillConverter {
    implicit val converter = new Converter {
        def toBuildToolStx(data: DataTypes): String = data match {
            case ScalaVersion(v) => s"""def scalaVersion = "${v}"""""
            case ScalaJSVersion(v) => s"""def scalaJSVersion = "${v}""""
            case Dependency(groupId, artifactId, version) => s"""ivy"${groupId}::${artifactId}:${version}""""
        }
    }  
}
