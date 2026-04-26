package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.Platform
import de.lolhens.minecraft.fluidphysics.config.Config.Commented.CommentedValueEncoder
import de.lolhens.minecraft.fluidphysics.config.Config.{Commented, configPath}
import io.circe.*
import io.circe.derivation.Configuration
import io.circe.syntax.*
import net.minecraft.resources.ResourceLocation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

trait Config[Self] {
  def default: Self

  def shouldUpdateConfig(config: Self): Boolean

  def migrateConfig(config: Self): Self = config

  protected def codec: Codec[Self]

  protected final def makeCodec(using decoder: Decoder[Self], encoder: Encoder[Self]): Codec[Self] =
    Codec.from(decoder, encoder)

  private given implicitCodec: Codec[Self] = codec

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
            (obj.asObject.flatMap(_("_comment")),
              obj.asObject.flatMap(_("value"))) match {
              case (Some(comment), Some(value)) =>
                List(comment)
                  .flatMap(_.asString)
                  .flatMap(_.split("\r?\n", -1).toList)
                  .zipWithIndex
                  .map { case (value, i) => s"_comment_${key}_$i" -> Json.fromString(value) } ++
                  List(key -> value)
              case _ =>
                List(key -> transformComments(obj))
            }
          case e =>
            List(e)
        }))

    import Config.Implicits.*
    transformComments(configJson).hoconString
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
      val config = migrateConfig(decodeString(configString))
      if (shouldUpdateConfig(config) && configString != encodeString(config))
        saveToPath(path, config)
      config
    }
  }
}

object Config {
  def configDirectory: Path = Platform.instance.configDir

  def configPath(modId: String): Path = configDirectory.resolve(s"$modId.conf")

  case class Commented[A](value: A, private val comment: Option[String]) {
    def withValue(value: A): Commented[A] = copy(value = value, comment = comment)
  }

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
      try f
      finally localValueEncoder.set(prevValueEncoder)
    }

    given decoder[A](using Decoder[A]): Decoder[Commented[A]] = Decoder[A].map(Commented(_, None))

    given encoder[A](using Encoder[A]): Encoder[Commented[A]] = Encoder.instance { value =>
      Option(localValueEncoder.get()).getOrElse(CommentedValueEncoder.Value).encode(value, summon[Encoder[A]])
    }

    given fromTuple[A]: Conversion[(A, String), Commented[A]] =
      tuple => Commented(tuple._1, Some(tuple._2).filter(_.nonEmpty))
  }

  object Implicits {
    private val spaces2 = Printer.spaces2.copy(colonLeft = "")
    private val commentRegex = "\"_comment_.*?\"\\s*?:\\s*?\"(.*)\",?".r

    extension (json: Json)
      def hoconString: String = json
        .printWith(spaces2)
        .pipe(commentRegex.replaceAllIn(_, e => "// " + e.group(1).replaceAll("\\\\\"", "\"")))
        .replaceAll("\"(.*?)\"\\s*?:\\s*", "$1 = ")

    given customConfig: Configuration = Configuration.default.withDefaults

    given identifierCodec: Codec[ResourceLocation] = Codec.from(
      Decoder.decodeString.map(ResourceLocation.tryParse),
      Encoder.encodeString.contramap[ResourceLocation](_.toString)
    )
  }
}
