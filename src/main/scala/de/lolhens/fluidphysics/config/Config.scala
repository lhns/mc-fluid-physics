package de.lolhens.fluidphysics.config

import java.nio.file.{Files, Path}

import de.lolhens.fluidphysics.FluidPhysicsMod
import de.lolhens.fluidphysics.config.Config.{RainRefill, Spring}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Printer}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

import scala.jdk.CollectionConverters._

case class Config(fluidWhitelist: Seq[Identifier] = Seq(Fluids.WATER, Fluids.LAVA).map(Registry.FLUID.getId),
                  findSourceMaxIterations: Int = 255,
                  flowOverSources: Boolean = true,
                  debugFluidState: Boolean = false,
                  spring: Option[Spring] = Some(Spring()),
                  rainRefill: Option[RainRefill] = Some(RainRefill())) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(Registry.FLUID.get)

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))
}

object Config {
  val default: Config = Config()

  case class Spring(block: Identifier = FluidPhysicsMod.SPRING_BLOCK_IDENTIFIER,
                    updateBlocksInWorld: Boolean = false,
                    allowInfiniteWater: Boolean = true) {
    lazy val getBlock: Block = Registry.BLOCK.get(block)
  }

  case class RainRefill(probability: Double = 0.2,
                        fluidWhitelist: Seq[Identifier] = Seq(Fluids.WATER).map(Registry.FLUID.getId)) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(Registry.FLUID.get)

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))
  }


  def configDirectory: Path = FabricLoader.getInstance().getConfigDirectory.toPath

  def configPath(modId: String): Path = configDirectory.resolve(s"$modId.conf")

  private implicit val customConfig: Configuration = Configuration.default.withDefaults

  implicit val identifierEncoder: Encoder[Identifier] = Encoder.encodeString.contramap[Identifier](_.toString)
  implicit val identifierDecoder: Decoder[Identifier] = Decoder.decodeString.map(Identifier.tryParse)

  private def loadFromPath(path: Path): Config =
    io.circe.config.parser.decodeFile[Config](path.toFile).toTry.get

  private val spaces2 = Printer.spaces2.copy(colonLeft = "")

  private def saveToPath(path: Path, config: Config): Unit =
    Files.write(path, List(config.asJson.printWith(spaces2)).asJava)

  def loadOrCreate(modId: String): Config = {
    val path = configPath(modId)
    if (Files.notExists(path)) {
      val config = default
      saveToPath(path, config)
      config
    } else {
      loadFromPath(path)
    }
  }
}
