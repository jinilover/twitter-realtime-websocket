package dto

case class HashtagAcrossStream(tuples: List[(String, Long)])

case class HashtagAcrossWindow(tuples: List[(String, Long)])

case class LanguageAcrossStream(tuples: List[(String, Long)])

case class LanguageAcrossWindow(tuples: List[(String, Long)])