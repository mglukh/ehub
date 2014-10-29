package nugget.agent

import play.api.libs.json.JsValue

/**
 * Created by maks on 18/09/14.
 */
package object controller {

  case class Handshake()

  case class CreateFlow(config: JsValue)
  case class StartFlow(flowId: Long)
  case class StopFlow(flowId: Long)


  case class MessageWithAttachments[T](msg: T, attachments: JsValue)
}
