package de.lolhens

import net.minecraft.util.math.Direction

import scala.jdk.CollectionConverters._

package object fluidphysics {
  val horizontal: Array[Direction] = Direction.Type.HORIZONTAL.iterator().asScala.toArray
}
