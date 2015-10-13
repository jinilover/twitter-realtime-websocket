#A prototype does simple analysis on Twitter stream data
This application does a simple real-time analysis on Twitter stream data.  It identifies the most popular hashtags or languages being used among the tweets (exclude the retweeted) since it started receiving the data or since the last minute.
It uses Spark streaming, Spark SQL, Akka, Play, WebSocket.  Most of the application is written in Scala except presenting the report is written in JavaScript.

##Application structure
* Tweet collector and analyzer
* Web application

##Tweet collector and analyzer
###Tweets collection
###Data analysis

##Web application

mentions how easy it is to use spark stream to avoid iteratees in play, because spark stream provides a good rich set of functions, mention spark sql
some side-effects still need, such as .cache(), the way to create a spark conf, need to save file, submit data to another place
mentions adonis-pickler can be used, but this is a simple application only needs to pickle a few simple data structure.
play websocket enable to use actor since 2.3
show the screen cap.
