package dto

case class HashtagAcrossStream(pairs: List[(String, Long)])

case class HashtagAcrossWindow(pairs: List[(String, Long)])

case class LanguageAcrossStream(pairs: List[(String, Long)])

case class LanguageAcrossWindow(pairs: List[(String, Long)])