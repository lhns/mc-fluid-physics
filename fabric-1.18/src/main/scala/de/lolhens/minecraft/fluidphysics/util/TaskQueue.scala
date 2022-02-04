package de.lolhens.minecraft.fluidphysics.util

import scala.collection.immutable.Queue

object TaskQueue {
  private var tasks = Queue.empty[() => Unit]
  val chunkSize = 100
  private var remainingChunkSize = chunkSize

  // process some tasks immediately
  def enqueue(f: () => Unit): Unit =
    if (remainingChunkSize > 0) {
      remainingChunkSize -= 1
      f()
    } else {
      tasks = tasks.enqueue(f)
    }

  def process(): Unit = {
    while (remainingChunkSize > 0 && tasks.nonEmpty) {
      remainingChunkSize -= 1
      val (head, tail) = tasks.dequeue
      tasks = tail
      head()
    }
    remainingChunkSize = chunkSize
  }
}
