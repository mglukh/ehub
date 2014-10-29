package nugget.core

sealed trait PipelineStateChangeRequest

case class BecomeActive() extends PipelineStateChangeRequest
case class BecomePassive() extends PipelineStateChangeRequest


