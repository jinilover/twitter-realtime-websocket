package controllers

import actors.TwitterAnalyticsActor
import akka.actor.ActorSystem
import play.api.libs.json.JsValue

//import spark._
import config.ConfigHelper
import play.api.mvc._
import play.api.Play.current
import play.api._

import scala.concurrent.Future

object Application extends Controller {
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def twitterAnalytics() = Action {
    implicit request =>
      Ok(views.html.analyticsPage(request))
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val configVals =
    for {
      systemName <- ConfigHelper.getString("actor.systemName")
      actorName <- ConfigHelper.getString("actor.name")
    } yield (systemName, actorName)

  def socket(): WebSocket[String, JsValue] =
    configVals fold(
      exception => WebSocket[String, JsValue](
        request => {
          Logger.error("fail to configure web socket", exception)
          Future(Left(InternalServerError(exception.getMessage)))
        }
      ),
      tuple => {
        val (systemName, actorName) = tuple
        WebSocket.acceptWithActor[String, JsValue] {
          request =>
            jsClient =>
              val props = TwitterAnalyticsActor.props(jsClient)
              val actorRef = ActorSystem(systemName).actorOf(props, actorName)
              Logger.info(s"actor path = $actorRef")
//              val selectedActor = system.actorSelection(selectionPath)
              props
        }
      })

}
