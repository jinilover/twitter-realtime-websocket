package spark

import actor.ActorLookup
import com.google.gson.Gson
import config.ConfigHelper
import dto.{LanguageAcrossWindow, LanguageAcrossStream, HashtagAcrossWindow, HashtagAcrossStream}
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkContext, SparkConf}
import twitter4j.auth.{OAuthAuthorization, Authorization}
import twitter4j.conf.ConfigurationBuilder

import scala.reflect.ClassTag

object TweetsAnalyzer extends App {
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
      intervalSecs <- ConfigHelper.getInt("spark.intervalSecs")
      noOfPartitions <- ConfigHelper.getInt("spark.noOfPartitions")
      tweetsDir <- ConfigHelper.getString("spark.tweetsDir")
      checkoutDir <- ConfigHelper.getString("spark.checkoutDir")
      consumerKey <- ConfigHelper.getString("twitter.consumerkey")
      consumerSecret <- ConfigHelper.getString("twitter.consumersecret")
      accessToken <- ConfigHelper.getString("twitter.accesstoken")
      accessTokenSecret <- ConfigHelper.getString("twitter.accesstokensecret")
    } yield (
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
        val (ssc, noOfPartitions, tweetsDir, checkoutDir, auth) = tuple
        collectTweets(ssc, noOfPartitions, tweetsDir, auth)
        analyzeTweets(ssc, tweetsDir, checkoutDir, selectionPath)
        ssc.start()
        ssc.awaitTermination()
      }
    ).merge
    case _ => println("missing actor selection path")
  }

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

  def analyzeTweets(ssc: StreamingContext, tweetsDir: String, checkpointDir: String, selectionPath: String): Unit = {
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
      _ reduceByKeyAndWindow(_ + _, Seconds(60))
    }

    List(
      (sameKeyWholeStream(hashPairs), HashtagAcrossStream(_: List[(String, Long)]), "hashtag across stream")
//      ,
//      (sameKeyWholeWindow(hashPairs), HashtagAcrossWindow(_: List[(String, Long)]), "hashtag across window"),
//      (sameKeyWholeStream(langPairs), LanguageAcrossStream(_: List[(String, Long)]), "languages across stream"),
//      (sameKeyWholeWindow(langPairs), LanguageAcrossWindow(_: List[(String, Long)]), "languages across window")
    ) foreach {
      tuple =>
        val (dstream, func, msg) = tuple
        dstream foreachRDD {
          rdd =>
            val pairs = rdd.take(topCount).toList
            println(
              s"""
                 |$msg,
                 |${pairs.mkString(",")}
             """.stripMargin)
            ActorLookup.lookup(selectionPath) ! func(pairs)
        }
    }

//    sameKeyWholeStream(hashPairs) foreachRDD {
//      rdd =>
//        val tuples = rdd.take(topCount).toList
//        println(
//          s"""
//             |hashtag across stream,
//             |${tuples.mkString(",")}
//           """.stripMargin)
//        ActorLookup.lookup(selectionPath) ! HashtagAcrossStream(tuples)
//    }
//
//    sameKeyWholeWindow(hashPairs) foreachRDD {
//      rdd => println(
//        s"""
//           |hashtag across window,
//           |${rdd.take(topCount).toList.mkString(",")}
//           """.stripMargin)
//    }
//
//    sameKeyWholeStream(langPairs) foreachRDD {
//      rdd => println(
//        s"""
//           |lang across stream,
//           |${rdd.take(topCount).toList.mkString(",")}
//           """.stripMargin)
//    }
//
//    sameKeyWholeWindow(langPairs) foreachRDD {
//      rdd => println(
//        s"""
//           |lang across window,
//           |${rdd.take(topCount).toList.mkString(",")}
//           """.stripMargin)
//    }
  }

  def createPairs[T, R: ClassTag](stream: DStream[T])(f: T => List[R]): DStream[(R, Long)] =
    stream
      .flatMap(f)
      .map((_, 1L))
      .reduceByKey(_ + _)
      .cache()

  def statefulTransform[R](stream: DStream[(R, Long)])(f: DStream[(R, Long)] => DStream[(R, Long)]): DStream[(R, Long)] =
    f(stream) transform (_ sortBy(_._2, ascending = false))
}
