package com.karfroth.scalackage.converter

import com.olvind.logging.{stdout, storing, LogLevel, Logger}

import org.scalablytyped.converter.{ Selection, Flavour }
import org.scalablytyped.converter.internal.{ IArray, InFolder, sets, files, constants }
import org.scalablytyped.converter.internal.importer._
import org.scalablytyped.converter.internal.importer.build.BloopCompiler
import org.scalablytyped.converter.internal.importer.documentation.Npmjs
import org.scalablytyped.converter.internal.importer.jsonCodecs._
import org.scalablytyped.converter.internal.phases.RecPhase
import org.scalablytyped.converter.internal.phases.PhaseListener.NoListener
import org.scalablytyped.converter.internal.scalajs.{ Name, Versions }
import org.scalablytyped.converter.internal.ts.CalculateLibraryVersion.PackageJsonOnly
import org.scalablytyped.converter.internal.ts.TsIdentLibrary

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable.SortedSet
import org.scalablytyped.converter.internal.phases.PhaseRes
import org.scalablytyped.converter.internal.phases.PhaseRunner
import org.scalablytyped.converter.internal.importer.build.PublishedSbtProject
import com.karfroth.scalackage.mill.Dependency
import scala.util.Try
import scala.util.Success
import org.scalablytyped.converter.internal.Json
import org.scalablytyped.converter.internal.ts.PackageJsonDeps

object TSConverter {

  type FailureType = Map[String, Either[Throwable, String]]

  class Paths(base: os.Path) {
    lazy val out: os.Path =
      files.existing(base / 'out)
    val node_modules: Option[os.Path] =
      Option(base / 'node_modules).filter(files.exists)
    val packageJson: Option[os.Path] =
      Option(base / "package.json").filter(files.exists)
  }

  val DefaultOptions = ConversionOptions(
    useScalaJsDomTypes     = true,
    outputPackage          = Name.typings,
    enableScalaJsDefined   = Selection.None,
    flavour                = Flavour.Normal,
    ignoredLibs            = Set(TsIdentLibrary("typescript")),
    ignoredModulePrefixes  = Set(),
    stdLibs                = IArray("es6"),
    expandTypeMappings     = EnabledTypeMappingExpansion.DefaultSelection,
    versions               = Versions(Versions.Scala213, Versions.ScalaJs1),
    organization           = "org.scalablytyped",
    enableReactTreeShaking = Selection.None,
    enableLongApplyMethod  = false,
  )

  def handleFailure(failures: FailureType) = {
    println(
      s"Failure: You might try --ignoredLibs ${failures.keys.mkString(", ")}",
    )

    failures.foreach {
      case (source, Left(value)) =>
        println(s"${source}:")
        value.printStackTrace()
      case (source, Right(value)) =>
        println(s"${source}: $value")
    }
  }

  def build(conversion: ConversionOptions): Either[FailureType, Seq[Dependency]] = {
    val paths = new Paths(os.pwd)
    val nodeModulesPathEither = paths.node_modules.map(Right.apply).getOrElse(Left(Map("node_modules" -> Left(new Exception("No Such Directory")))))
    val packageJsonPathEither = paths.packageJson.map(Right.apply).getOrElse(Left(Map("package.json" -> Left(new Exception("No Such File")))))
    for {
      nodeModulesPath <- nodeModulesPathEither
      packageJsonPath <- packageJsonPathEither
      result <- buildInternal(conversion, paths.out, nodeModulesPath, packageJsonPath)
    } yield result
  }

  def buildInternal(conversion: ConversionOptions, out: os.Path, nodeModulesPath: os.Path, packageJsonPath: os.Path) = {
    val t0 = System.currentTimeMillis

    val logger: Logger[(Array[Logger.Stored], Unit)] =
      storing().zipWith(stdout.filter(LogLevel.warn))

    val compiler = Await.result(
      BloopCompiler(logger.filter(LogLevel.debug).void, conversion.versions, failureCacheFolderOpt = None),
      Duration.Inf,
    )
    
    println(packageJsonPath)
    val packageJson = Json.force[PackageJsonDeps](packageJsonPath)
    val wantedLibs = {
      val fromPackageJson = packageJson.allLibs(true, peer = false).map(TsIdentLibrary.apply)
      val ret = fromPackageJson -- conversion.ignoredLibs
      ret
    }

    if (wantedLibs.isEmpty) {
      Left(Map("package.json" -> "dependencies in package.json is empty"))
    }

    val bootstrapped = Bootstrap.fromNodeModules(InFolder(nodeModulesPath), conversion, wantedLibs)
    val sources: Vector[Source.TsLibSource] = {
      bootstrapped.initialLibs match {
        case Left(unresolved) => sys.error(unresolved.msg)
        case Right(initial)   => initial
      }
    }

    val parseCachePath = Some(files.existing(constants.defaultCacheFolder / 'parse).toNIO)
    val pipeline =
      RecPhase[Source]
        .next(
          new Phase1ReadTypescript(
            calculateLibraryVersion = PackageJsonOnly,
            resolve                 = bootstrapped.libraryResolver,
            ignored                 = conversion.ignoredLibs,
            ignoredModulePrefixes   = conversion.ignoredModulePrefixes,
            pedantic                = false,
            parser                  = PersistingParser(parseCachePath, bootstrapped.folders, logger.void),
            expandTypeMappings      = conversion.expandTypeMappings,
          ),
          "typescript",
        )
        .next(
          new Phase2ToScalaJs(
            pedantic             = false,
            enableScalaJsDefined = conversion.enableScalaJsDefined,
            outputPkg            = conversion.outputPackage,
            flavour              = conversion.flavourImpl,
          ),
          "scala.js",
        )
        .next(new PhaseFlavour(conversion.flavourImpl), conversion.flavourImpl.toString)
        .next(
          new Phase3Compile(
            versions                   = conversion.versions,
            compiler                   = compiler,
            targetFolder               = out,
            organization               = conversion.organization,
            publisherOpt               = None,
            publishLocalFolder         = constants.defaultLocalPublishFolder,
            metadataFetcher            = Npmjs.No,
            softWrites                 = true,
            flavour                    = conversion.flavourImpl,
            generateScalaJsBundlerFile = false,
            ensureSourceFilesWritten   = true,
          ),
          "build",
        )
    val results: Map[Source, PhaseRes[Source, PublishedSbtProject]] =
      sources.toVector
        .map(source => source -> PhaseRunner.go(pipeline, source, Nil, (_: Source) => logger.void, NoListener))
        .toMap
    val td = System.currentTimeMillis - t0
    logger.warn(td)

    val failures: FailureType =
      results
        .collect { case (_, PhaseRes.Failure(errors)) => errors }
        .reduceOption(_ ++ _)
        .getOrElse(Map.empty)
        .map{ case (source, error) => source.libName.value -> error}

    if (failures.nonEmpty) {
      Left(failures)
    } else {
      val allSuccesses: Map[Source, PublishedSbtProject] = {
        def go(source: Source, p: PublishedSbtProject): Map[Source, PublishedSbtProject] =
          Map(source -> p) ++ p.project.deps.flatMap { case (k, v) => go(k, v) }

        results.collect { case (s, PhaseRes.Ok(res)) => go(s, res) }.reduceOption(_ ++ _).getOrElse(Map.empty)
      }

      val short =
        results
          .collect { case (_, PhaseRes.Ok(res)) => res.project }
          .toSeq
          .filter(_.name != Name.std.unescaped)
          .sortBy(_.name)

      println()
      println(s"Successfully converted ${allSuccesses.keys.map(x => x.libName.value).mkString(", ")}")
      println("To use in sbt: ")
      println(s"""libraryDependencies ++= Seq(
            |  ${short.map(p => p.reference.asSbt).mkString("", ",\n  ", "")}
            |)""".stripMargin)

      Right(short.map(p => Dependency(p.reference.org, p.name, p.reference.version)))
    }
  }
}
