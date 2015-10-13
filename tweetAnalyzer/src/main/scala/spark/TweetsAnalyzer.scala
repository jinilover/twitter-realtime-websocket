package spark

import actor.ActorLookup
import com.google.gson.Gson
import com.typesafe.scalalogging.LazyLogging
import config.ConfigHelper
import dto._
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkContext, SparkConf}
import twitter4j.auth.{OAuthAuthorization, Authorization}
import twitter4j.conf.ConfigurationBuilder

import scala.reflect.ClassTag

object TweetsAnalyzer extends App with LazyLogging {
  lazy val conf = new SparkConf().setMaster("local[*]").setAppName("TweetsCollector")
  lazy val sc = new SparkContext(conf)

  val auth: String => String => String => String => Option[Authorization] =
    consumerKey => consumerSecret => token => tokenSecret =>
      Some {
        new OAuthAuthorization(
          new ConfigurationBuilder()
            .setOAuthConsumerKey(consumerKey)
            .setOAuthConsumerSecret(consumerSecret)
            .setOAuthAccessToken(token)
            .setOAuthAccessTokenSecret(tokenSecret)
            .build()
        )
      }

  lazy val configVals =
    for {
      windowInterval <- ConfigHelper.getInt("spark.windowInterval")
      intervalSecs <- ConfigHelper.getInt("spark.intervalSecs")
      noOfPartitions <- ConfigHelper.getInt("spark.noOfPartitions")
      tweetsDir <- ConfigHelper.getString("spark.tweetsDir")
      checkoutDir <- ConfigHelper.getString("spark.checkoutDir")
      consumerKey <- ConfigHelper.getString("twitter.consumerkey")
      consumerSecret <- ConfigHelper.getString("twitter.consumersecret")
      accessToken <- ConfigHelper.getString("twitter.accesstoken")
      accessTokenSecret <- ConfigHelper.getString("twitter.accesstokensecret")
    } yield (
      windowInterval,
      new StreamingContext(sc, Seconds(intervalSecs)),
      noOfPartitions,
      tweetsDir,
      checkoutDir,
      auth(consumerKey)(consumerSecret)(accessToken)(accessTokenSecret))

  args.toList match {
    // selectionPath should be sthing like akka.tcp://twitter-analytics-system@127.0.0.1:57190/user/twitterAnalytics
    case selectionPath :: _ => configVals.bimap(
      print(_),
      tuple => {
        val (windowInterval, ssc, noOfPartitions, tweetsDir, checkoutDir, auth) = tuple
        collectTweets(ssc, noOfPartitions, tweetsDir, auth)
        analyzeTweets(windowInterval, ssc, tweetsDir, checkoutDir, selectionPath)
        ssc.start()
        ssc.awaitTermination()
      }
    ).merge
    case _ => println("missing actor selection path")
  }

  /**
   * Collect tweets from the real-time twitter stream, convert them to json format and stored to file
   */
  def collectTweets(ssc: StreamingContext, noOfPartitions: Int, tweetsDir: String, auth: Option[Authorization]): Unit = {
    val tweetStream = TwitterUtils.createStream(ssc, auth)
      .filter(!_.isRetweet)
      .map(new Gson().toJson(_))

    tweetStream foreachRDD ((rdd, time) => {
      val count = rdd.count()
      if (count > 0) {
        val outputRDD = rdd.repartition(noOfPartitions)
        outputRDD.saveAsTextFile(tweetsDir + "/tweets_" + time.milliseconds.toString)
      }
    })
  }

  /**
   * Loads the data from the stored json file by collectTweets, find the popular hashtags/languages
   * among the tweets using spark stream api, sends the analyzed result to an Akka actor run by
   * another jvm
   */
  def analyzeTweets(windowInterval: Int, ssc: StreamingContext, tweetsDir: String, checkpointDir: String, selectionPath: String): Unit = {
    val topCount = 10
    ssc.checkpoint(checkpointDir)

    val tweets = ssc.textFileStream(tweetsDir)

    val selectedData: DStream[(String, String)] = tweets.transform {
      rdd =>
        if (rdd.isEmpty())
          rdd.sparkContext.parallelize(List.empty[(String, String)])
        else {
          val sqlContext = SQLContext.getOrCreate(rdd.sparkContext)
          val table = "tweets"
          (sqlContext.read json rdd).registerTempTable(table)
          sqlContext.sql(s"select text, user.lang from $table") map (row => (row.getString(0), row.getString(1)))
        }
    }.cache()

    val pairsFunc = createPairs[(String, String), String](selectedData) _

    val hashPairs = pairsFunc {
      t =>
        val split: List[String] => List[String] =
          strings => List(" ", "\n", ",", ":", ";", "\\.").foldLeft(strings) {
            (z, delim) => z flatMap (_ split delim)
          }
        split(List(t._1)).filter(_ startsWith "#")
    }

    val langPairs = pairsFunc(t => List(t._2))

    val sameKeyWholeStream = statefulTransform(_: DStream[(String, Long)]) {
      _ updateStateByKey ((noOfSameKey, state) => Some(state.getOrElse(0L) + noOfSameKey.sum))
    }

    val sameKeyWholeWindow = statefulTransform(_: DStream[(String, Long)]) {
      _ reduceByKeyAndWindow(_ + _, Seconds(windowInterval))
    }

    List(
      (sameKeyWholeStream(hashPairs), "popularHashtagSoFar"),
      (sameKeyWholeWindow(hashPairs), "popularHashtagLastPeriod"),
      (sameKeyWholeStream(langPairs), "popularLanguageSoFar"),
      (sameKeyWholeWindow(langPairs), "popularLanguageLastPeriod")
    ) foreach {
      tuple =>
        val (dstream, msg) = tuple
        dstream foreachRDD {
          rdd =>
            val pairs = rdd.take(topCount).toList
            logger.debug(
              s"""
                 |$msg,
                 |${pairs.mkString(",")}
             """.stripMargin)
            ActorLookup.lookup(selectionPath) ! MostPopular(msg, pairs)
        }
    }
  }

  /**
   * functional template for code sharing via currying
   */
  def createPairs[T, R: ClassTag](stream: DStream[T])(f: T => List[R]): DStream[(R, Long)] =
    stream
      .flatMap(f)
      .map((_, 1L))
      .reduceByKey(_ + _)
      .cache()

  /**
   * functional template for code sharing via currying
   */
  def statefulTransform[R](stream: DStream[(R, Long)])(f: DStream[(R, Long)] => DStream[(R, Long)]): DStream[(R, Long)] =
    f(stream) transform (_ sortBy(_._2, ascending = false))
}
