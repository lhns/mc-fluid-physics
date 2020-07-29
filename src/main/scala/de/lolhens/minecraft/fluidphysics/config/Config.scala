package de.lolhens.minecraft.fluidphysics.config

import java.nio.file.{Files, Path}

import de.lolhens.minecraft.fluidphysics.config.Config.{configPath, spaces2}
import io.circe.generic.extras.{AutoDerivation, Configuration}
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder, Printer}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier

import scala.jdk.CollectionConverters._

trait Config[Self] extends Config.Implicits {
  def default: Self

  protected def codec: Codec[Self]

  protected final def makeCodec(implicit decoder: Decoder[Self], encoder: Encoder[Self]): Codec[Self] =
    Codec.from(decoder, encoder)

  private implicit lazy val implicitCodec: Codec[Self] = codec

  private def loadFromPath(path: Path): Self =
    io.circe.config.parser.decodeFile[Self](path.toFile).toTry.get

  private def saveToPath(path: Path, config: Self): Unit =
    Files.write(path, List(config.asJson.printWith(spaces2)).asJava)

  def loadOrCreate(modId: String): Self = {
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

object Config {
  def configDirectory: Path = FabricLoader.getInstance().getConfigDirectory.toPath

  def configPath(modId: String): Path = configDirectory.resolve(s"$modId.conf")

  private val spaces2 = Printer.spaces2.copy(colonLeft = "")

  trait Implicits extends AutoDerivation {

    import Implicits._

    protected implicit def implicitCustomConfig: Configuration = customConfig

    implicit def implicitIdentifierCodec: Codec[Identifier] = identifierCodec
  }

  object Implicits {
    private val customConfig: Configuration = Configuration.default.withDefaults

    private val identifierCodec: Codec[Identifier] = Codec.from(
      Decoder.decodeString.map(Identifier.tryParse),
      Encoder.encodeString.contramap[Identifier](_.toString)
    )
  }

}
