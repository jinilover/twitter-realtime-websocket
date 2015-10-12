package controllers

import actors.{TwitterAnalyticsActor, ActorCommons}
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

  import ActorCommons._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scalaz.\/

  lazy val configVals =
    for {
      actorName <- ConfigHelper.getString("actor.name")
      selectionPath <- ConfigHelper.getString("actor.selectionPath")
      intervalSecs <- ConfigHelper.getInt("spark.intervalSecs")
      noOfPartitions <- ConfigHelper.getInt("spark.noOfPartitions")
      tweetsDir <- ConfigHelper.getString("spark.tweetsDir")
      checkoutDir <- ConfigHelper.getString("spark.checkoutDir")
      consumerKey <- ConfigHelper.getString("twitter.consumerkey")
      consumerSecret <- ConfigHelper.getString("twitter.consumersecret")
      accessToken <- ConfigHelper.getString("twitter.accesstoken")
      accessTokenSecret <- ConfigHelper.getString("twitter.accesstokensecret")
    } yield (actorName,
      selectionPath,
      intervalSecs,
      noOfPartitions,
      tweetsDir,
      checkoutDir,
      consumerKey,
      consumerSecret,
      accessToken,
      accessTokenSecret)

  def socket(): WebSocket[String, String] =
    configVals fold(
      exception => WebSocket[String, String](
        request => {
          Logger.error("fail to configure web socket", exception)
          Future(Left(InternalServerError(exception.getMessage)))
        }
      ),
      tuple => {
        val (actorName, selectionPath, intervalSecs, noOfPartitions, tweetsDir, checkoutDir,
        consumerKey, consumerSecret, accessToken, accessTokenSecret) = tuple
        WebSocket.acceptWithActor[String, String] {
          request =>
            jsClient =>
              val props = TwitterAnalyticsActor.props(jsClient)
              val actorRef = system.actorOf(props, actorName)
              //          CollectTweets.collect(ssc, noOfPartitions, tweetsDir, consumerKey, consumerSecret, accessToken, accessTokenSecret)
              //              AnalyzeTweets1.findPopulars(ssc, tweetsDir, checkoutDir, selectionPath)

              //              ssc.start()
              //              ssc.awaitTermination()
              Logger.info(s"actor path = $actorRef")
//              val selectedActor = system.actorSelection(selectionPath)
              props
        }
      })

}
