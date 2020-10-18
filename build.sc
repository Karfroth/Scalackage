import mill._, scalalib._
import coursier.maven.MavenRepository

object scalackage extends ScalaModule {
    def scalaVersion = "2.12.12"
    def ivyDeps = Agg(
        ivy"com.lihaoyi::ujson:1.2.0"
    ,   ivy"org.scalablytyped.converter::importer:1.0.0-beta27"    
    ,   ivy"org.scalablytyped.converter::phases:1.0.0-beta27"    
    ,   ivy"org.scalablytyped.converter::ts:1.0.0-beta27"    
    )
    def repositories = super.repositories ++ Seq(
        MavenRepository("https://dl.bintray.com/oyvindberg/converter")
    )
}