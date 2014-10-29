package controllers.web

import actors.WebsocketActor
import models._
import play.api._
import play.api.mvc._
import play.api.Play.current

object Application extends Controller {

	def index = Action { implicit request =>
		val computers = Computer.list
		Ok(views.html.web.index("Hello! I'm the WEB!", computers))
	}


	def hey = Action {
		Ok(views.html.web.rjs(""))
	}

	def socket = WebSocket.acceptWithActor[String, String] {
		req => actor =>
			println("!>>>>> ")
			WebsocketActor.props(actor)
	}



}