package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.config.Config.Commented.CommentedValueEncoder
import de.lolhens.minecraft.fluidphysics.config.Config.{Commented, configPath, spaces2}
import io.circe._
import io.circe.generic.extras.{AutoDerivation, Configuration}
import io.circe.syntax._
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.loading.FMLPaths

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

trait Config[Self] extends Config.Implicits {
  def default: Self

  def updateConfig(config: Self): Boolean

  protected def codec: Codec[Self]

  protected final def makeCodec(implicit decoder: Decoder[Self], encoder: Encoder[Self]): Codec[Self] =
    Codec.from(decoder, encoder)

  private implicit lazy val implicitCodec: Codec[Self] = codec

  def decodeString(string: String): Self =
    io.circe.config.parser.decode[Self](string).toTry.get

  def encodeString(config: Self): String = {
    val configJson =
      Commented.overrideEncoder(CommentedValueEncoder.Raw)(config.asJson)
        .deepMerge(Commented.overrideEncoder(CommentedValueEncoder.Comment)(default.asJson))

    def transformComments(json: Json): Json =
      json
        .mapArray(_.map(transformComments))
        .mapObject(obj => JsonObject.fromIterable(obj.toIterable.flatMap {
          case (key, obj) if obj.isObject =>
            (obj.asObject.flatMap(_ ("_comment")),
              obj.asObject.flatMap(_ ("value"))) match {
              case (Some(comment), Some(value)) =>
                Some(comment)
                  .filterNot(_.isNull)
                  .map(s"_comment_$key" -> _)
                  .toList ++
                  List(key -> value)
              case _ =>
                List(key -> transformComments(obj))
            }
          case e =>
            List(e)
        }))

    transformComments(configJson)
      .printWith(spaces2)
      .replaceAll("\"_comment_.*?\"\\s*?:\\s*?\"(.*)\",?", "// $1")
      .replaceAll("\"(.*?)\"\\s*?:\\s*", "$1 = ")
  }

  private def saveToPath(path: Path, config: Self): Unit =
    Files.write(path, List(encodeString(config)).asJava, StandardCharsets.UTF_8)

  def loadOrCreate(modId: String): Self = {
    val path = configPath(modId)
    if (Files.notExists(path)) {
      val config = default
      saveToPath(path, config)
      config
    } else {
      val configString = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.mkString("\n")
      val config = decodeString(configString)
      if (updateConfig(config) && configString != encodeString(config))
        saveToPath(path, config)
      config
    }
  }
}

object Config {
  def configDirectory: Path = FMLPaths.CONFIGDIR.get()

  def configPath(modId: String): Path = configDirectory.resolve(s"$modId.conf")

  private val spaces2 = Printer.spaces2.copy(colonLeft = "")

  case class Commented[A](value: A, private val comment: Option[String])

  object Commented {

    trait CommentedValueEncoder {
      def encode[A](value: Commented[A], encoder: Encoder[A]): Json
    }

    object CommentedValueEncoder {

      object Value extends CommentedValueEncoder {
        override def encode[A](value: Commented[A], encoder: Encoder[A]): Json = encoder(value.value)
      }

      private def comment[A](value: Commented[A]): (String, Json) =
        "_comment" -> value.comment.fold(Json.Null)(Json.fromString)

      object Raw extends CommentedValueEncoder {
        override def encode[A](value: Commented[A], encoder: Encoder[A]): Json =
          Json.fromFields(List(comment(value), "value" -> encoder(value.value)))
      }

      object Comment extends CommentedValueEncoder {
        override def encode[A](value: Commented[A], encoder: Encoder[A]): Json =
          Json.fromFields(List(comment(value)))
      }

    }

    private val localValueEncoder: ThreadLocal[CommentedValueEncoder] = new ThreadLocal()

    def overrideEncoder[A](encoder: CommentedValueEncoder)(f: => A): A = {
      val prevValueEncoder = localValueEncoder.get()
      localValueEncoder.set(encoder)
      val result = f
      localValueEncoder.set(prevValueEncoder)
      result
    }

    implicit def decoder[A: Decoder]: Decoder[Commented[A]] = Decoder[A].map(Commented(_, None))

    implicit def encoder[A: Encoder]: Encoder[Commented[A]] = Encoder.instance { value =>
      Option(localValueEncoder.get()).getOrElse(CommentedValueEncoder.Value).encode(value, Encoder[A])
    }

    implicit def fromTuple[A](tuple: (A, String)): Commented[A] = Commented(tuple._1, Some(tuple._2).filter(_.nonEmpty))
  }

  trait Implicits extends AutoDerivation {

    import Implicits._

    protected implicit def implicitCustomConfig: Configuration = customConfig

    implicit def implicitIdentifierCodec: Codec[ResourceLocation] = identifierCodec
  }

  object Implicits {
    private val customConfig: Configuration = Configuration.default.withDefaults

    private val identifierCodec: Codec[ResourceLocation] = Codec.from(
      Decoder.decodeString.map(ResourceLocation.tryCreate),
      Encoder.encodeString.contramap[ResourceLocation](_.toString)
    )
  }

}
