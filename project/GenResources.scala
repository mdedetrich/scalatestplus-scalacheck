/*
* Copyright 2001-2015 Artima, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.io._
import scala.io.Source
import scala.util.parsing.combinator.JavaTokenParsers

trait GenResources {

  val generatorSource = new File("GenResources.scala")

  def packageName: String = "scalacheck"

  def resourcesTemplate(methods: String): String

  def failureMessagesTemplate(methods: String): String

  def resourcesKeyValueTemplate(kv: KeyValue, paramCount: Int): String

  def failureMessagesKeyValueTemplate(kv: KeyValue, paramCount: Int): String

  def propertiesFile: File = new File("scalatestPlusScalaCheck/.jvm/src/main/resources/org/scalatestplus/scalacheck/MessageBundle.properties")

  val paramRegex = "\\{\\d+\\}".r

  case class KeyValue(key: String, value: String)

  object KeyValueParser extends JavaTokenParsers {
    def equalParser: Parser[String] = "="
    def keyParser: Parser[String] = """([\p{L}_$][\p{L}\p{N}_$]*\.)*[\p{L}_$][\p{L}\p{N}_$]*""".r  // use Java class name parser for now for key
    def valueParser: Parser[String] = ".*".r
    def keyValueParser: Parser[KeyValue] = keyParser ~ equalParser ~ valueParser ^^ {
      case ~(~(key, _), value) => KeyValue(key, value)
    }
    def parse(s: String): KeyValue =
      parseAll(keyValueParser, s) match {
        case Success(kv, _) => kv
        case other => throw new RuntimeException("invalid key value syntax: " + s)
      }
  }

  def genResources(targetDir: File, version: String, scalaVersion: String): Seq[File] = {
    targetDir.mkdirs()

    val sourcePropertiesFile = propertiesFile
    val lines = Source.fromFile(sourcePropertiesFile).getLines

    val resourcesMethods =
      lines.filter(!_.trim.isEmpty).map { line =>
        val kv = KeyValueParser.parse(line)
        val paramTokens = paramRegex.findAllIn(kv.value)
        val paramCount = if (paramTokens.isEmpty) 0 else paramTokens.map(t => t.substring(1, t.length - 1).toInt).max + 1
        resourcesKeyValueTemplate(kv, paramCount)
      }.mkString("\n\n")

    val resourcesFile = new File(targetDir, "Resources.scala")
    if (!resourcesFile.exists || generatorSource.lastModified > resourcesFile.lastModified) {
      val resourcesWriter = new BufferedWriter(new FileWriter(resourcesFile))
      try {
        resourcesWriter.write(resourcesTemplate(resourcesMethods))
      }
      finally {
        resourcesWriter.flush()
        resourcesWriter.close()
        println("Generated " + resourcesFile.getAbsolutePath)
      }
    }

    Vector(resourcesFile)
  }

  def genFailureMessages(targetDir: File, version: String, scalaVersion: String): Seq[File] = {
    targetDir.mkdirs()

    val sourcePropertiesFile = propertiesFile
    val lines = Source.fromFile(sourcePropertiesFile).getLines

    val failureMessagesMethods =
      lines.filter(!_.trim.isEmpty).map { line =>
        val kv = KeyValueParser.parse(line)
        val paramTokens = paramRegex.findAllIn(kv.value)
        val paramCount = if (paramTokens.isEmpty) 0 else paramTokens.map(t => t.substring(1, t.length - 1).toInt).max + 1
        failureMessagesKeyValueTemplate(kv, paramCount)
      }.mkString("\n\n")

    val failureMessagesFile = new File(targetDir, "FailureMessages.scala")
    if (!failureMessagesFile.exists || generatorSource.lastModified > failureMessagesFile.lastModified) {
      val failureMessagesWriter = new BufferedWriter(new FileWriter(failureMessagesFile))
      try {
        failureMessagesWriter.write(failureMessagesTemplate(failureMessagesMethods))
      }
      finally {
        failureMessagesWriter.flush()
        failureMessagesWriter.close()
        println("Generated " + failureMessagesFile.getAbsolutePath)
      }
    }

    Vector(failureMessagesFile)
  }

}

object GenResourcesJVM extends GenResources {

  def resourcesTemplate(methods: String): String =
    s"""package org.scalatestplus.$packageName
        |
        |import java.util.ResourceBundle
        |import java.text.MessageFormat
        |
        |private[$packageName] object Resources {
        |
        |lazy val resourceBundle = ResourceBundle.getBundle("org.scalatestplus.scalacheck.MessageBundle")
        |
        |def makeString(resourceName: String, args: Array[Any]): String = {
        |  val raw = resourceBundle.getString(resourceName)
        |  formatString(raw, args)
        |}
        |
        |def formatString(rawString: String, args: Array[Any]): String = {
        |  val msgFmt = new MessageFormat(rawString)
        |  msgFmt.format(args.toArray)
        |}
        |
        |$methods
        |
        |def bigProblems(ex: Throwable): String = {
        |  val message = if (ex.getMessage == null) "" else ex.getMessage.trim
        |  if (message.length > 0) Resources.bigProblemsWithMessage(message) else Resources.bigProblems()
        |}
        |}
    """.stripMargin

  def failureMessagesTemplate(methods: String): String =
    s"""package org.scalatestplus.$packageName
       |
       |private[$packageName] object FailureMessages {
       |
       |def decorateToStringValue(prettifier: org.scalactic.Prettifier, o: Any): String = prettifier.apply(o)
       |
       |$methods
       |
       |}
    """.stripMargin

  def resourcesKeyValueTemplate(kv: KeyValue, paramCount: Int): String =
    (
      if (paramCount > 0)
        "def " + kv.key + "(" + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = makeString(\"" + kv.key + "\", Array(" + (for (i <- 0 until paramCount) yield s"param$i").mkString(", ") + "))"
      else
        "def " + kv.key + "(): String = resourceBundle.getString(\"" + kv.key + "\")"
    ) + "\n\n" +
    "def raw" + kv.key.capitalize + ": String = resourceBundle.getString(\"" + kv.key + "\")"

  def failureMessagesKeyValueTemplate(kv: KeyValue, paramCount: Int): String =
    "def " + kv.key + (if (paramCount == 0) "(" else "(prettifier: org.scalactic.Prettifier, ") + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = Resources." + kv.key + "(" + (for (i <- 0 until paramCount) yield s"prettifier.apply(param$i)").mkString(", ") + ")"

}

object GenResourcesJSVM extends GenResources {

  def resourcesTemplate(methods: String): String =
    s"""package org.scalatestplus.$packageName
        |
        |private[$packageName] object Resources {
        |
        |def formatString(rawString: String, args: Array[Any]): String = {
        |  args.zipWithIndex.foldLeft(rawString) { case (result, (arg, idx)) =>
        |    result.replaceAllLiterally("{" + idx + "}", arg.toString())
        |  }
        |}
        |
        |$methods
        |
        |def bigProblems(ex: Throwable): String = {
        |  val message = if (ex.getMessage == null) "" else ex.getMessage.trim
        |  if (message.length > 0) Resources.bigProblemsWithMessage(message) else Resources.bigProblems()
        |}
        |}
    """.stripMargin

  def failureMessagesTemplate(methods: String): String =
    s"""package org.scalatestplus.$packageName
        |
        |private[$packageName] object FailureMessages {
        |
        |def decorateToStringValue(prettifier: org.scalactic.Prettifier, o: Any): String = org.scalactic.Prettifier.default(o)
        |
        |$methods
        |
        |}
    """.stripMargin

  def resourcesKeyValueTemplate(kv: KeyValue, paramCount: Int): String =
    "final val raw" + kv.key.capitalize + " = \"" + kv.value.replaceAllLiterally("\"", "\\\"").replaceAllLiterally("''", "'") + "\"\n\n" +
    (
      if (paramCount == 0 )
        "final def " + kv.key + "() = raw" + kv.key.capitalize
      else
        "def " + kv.key + "(" + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = \n" +
        "  raw" + kv.key.capitalize + (for (i <- 0 until paramCount) yield ".replaceAllLiterally(\"{" + i + "}\", param" + i + ".toString())").mkString + "\n"
        /*"object " + kv.key + " { \ndef apply(" + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = \n" +
        "  raw" + kv.key.capitalize + (for (i <- 0 until paramCount) yield ".replaceAllLiterally(\"{" + i + "}\", param" + i + " + \"\")").mkString + "\n" +
        "}"*/
    )
    /*"def " + kv.key + "(" + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = \n" +
    "  raw" + kv.key.capitalize + (for (i <- 0 until paramCount) yield ".replaceAllLiterally(\"{" + i + "}\", param" + i + " + \"\")").mkString + "\n\n" +
    "val raw" + kv.key.capitalize + ": String = \"" + kv.value.replaceAllLiterally("\"", "\\\"") + "\""*/

  def failureMessagesKeyValueTemplate(kv: KeyValue, paramCount: Int): String =
    if (paramCount == 0)
      "final def " + kv.key + "() = Resources." + kv.key + "()"
    else
      "object " + kv.key + " { \ndef apply(" + (if (paramCount == 0) "" else "prettifier: org.scalactic.Prettifier, ") + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = \n" +
      "  Resources." + kv.key + "(" + (for (i <- 0 until paramCount) yield s"decorateToStringValue(prettifier, param$i)").mkString(", ") + ")" + "\n" +
      "}"
      //"def " + kv.key + "(" + (for (i <- 0 until paramCount) yield s"param$i: Any").mkString(", ") + "): String = Resources." + kv.key + "(" + (for (i <- 0 until paramCount) yield s"decorateToStringValue(param$i)").mkString(", ") + ")"

}
