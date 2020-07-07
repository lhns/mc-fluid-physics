package de.lolhens.fluidphysics.config

import java.nio.file.{Files, Path}

import de.lolhens.fluidphysics.FluidPhysicsMod
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier

import scala.jdk.CollectionConverters._

case class Config(debugFluidState: Boolean = false,
                  springBlock: Option[Identifier] = Some(FluidPhysicsMod.SPRING_BLOCK_IDENTIFIER),
                  springAllowsInfiniteWater: Boolean = true,
                  flowOverSources: Boolean = true,
                  enabledForWater: Boolean = true,
                  enabledForLava: Boolean = true)

object Config {
  val empty: Config = Config()

  def configDirectory: Path = FabricLoader.getInstance().getConfigDirectory.toPath

  def configPath(modId: String): Path = configDirectory.resolve(s"$modId.conf")

  private implicit val customConfig: Configuration = Configuration.default.withDefaults

  implicit val identifierEncoder: Encoder[Identifier] = Encoder.encodeString.contramap[Identifier](_.toString)
  implicit val identifierDecoder: Decoder[Identifier] = Decoder.decodeString.map(Identifier.tryParse)

  private def loadFromPath(path: Path): Config =
    io.circe.config.parser.decodeFile[Config](path.toFile).toTry.get

  private def saveToPath(path: Path, config: Config): Unit =
    Files.write(path, List(config.asJson.spaces2).asJava)

  def loadOrCreate(modId: String): Config = {
    val path = configPath(modId)
    if (Files.notExists(path)) {
      val config = empty
      saveToPath(path, config)
      config
    } else {
      loadFromPath(path)
    }
  }
}
