import mill._, scalalib._

object scalackage extends ScalaModule {
    def scalaVersion = "2.13.3"
    def ivyDeps = Agg(
        ivy"com.lihaoyi::ujson:1.2.0"
    )
}