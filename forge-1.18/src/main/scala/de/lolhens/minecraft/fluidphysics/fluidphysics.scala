package de.lolhens.minecraft

import net.minecraft.core.Direction

import scala.jdk.CollectionConverters._

package object fluidphysics {
  val horizontal: Array[Direction] = Direction.Plane.HORIZONTAL.iterator().asScala.toArray
}
