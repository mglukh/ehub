package agent

import scala.concurrent.duration.DurationDouble

/**
 * Created by L045682 on 15/09/2014.
 */
case class DataChunk[T, C <: Cursor](data: Option[T], cursor: C, hasMore: Boolean)


trait ResourcePullingProxy[T, C <: Cursor] {

  def pullRetryInterval = 3.second

  def next(c: Option[C]): Option[DataChunk[T, C]]

  def cancelResource()
}
