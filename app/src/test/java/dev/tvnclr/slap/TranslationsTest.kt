package dev.tvnclr.slap

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every translatable string and plural of the app and of tvnclr-ui must exist in each locale of
 * [REQUIRED_LOCALES] with the same format arguments. Keeps half-translated screens out of builds.
 */
class TranslationsTest {

  private val REQUIRED_LOCALES = listOf("b+pt+BR")

  private val sources = listOf("strings.xml" to resDir("app"), "tvnclr_ui_strings.xml" to resDir("tvnclr-ui"))

  private fun resDir(module: String): File {
    val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    return File(root, "$module/src/main/res")
  }

  /** name -> all texts (plural items joined), translatable only. */
  private fun entries(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val out = mutableMapOf<String, String>()
    for (tag in listOf("string", "plurals")) {
      val nodes = doc.getElementsByTagName(tag)
      for (i in 0 until nodes.length) {
        val e = nodes.item(i) as Element
        if (e.getAttribute("translatable") == "false") continue
        out[tag + ":" + e.getAttribute("name")] = e.textContent
      }
    }
    return out
  }

  private val formatArg = Regex("%(\\d+\\$)?[sdf]")

  private fun args(text: String) = formatArg.findAll(text).map { it.value }.toSet()

  @Test
  fun everyStringIsTranslated() {
    val problems = mutableListOf<String>()
    for ((fileName, res) in sources) {
      val base = entries(File(res, "values/$fileName"))
      for (locale in REQUIRED_LOCALES) {
        val translated = entries(File(res, "values-$locale/$fileName"))
        for ((name, text) in base) {
          val t = translated[name]
          if (t == null) problems += "$locale/$fileName: missing $name"
          else if (args(text) != args(t)) problems += "$locale/$fileName: format arguments differ in $name"
        }
      }
    }
    assertTrue(problems.joinToString("\n", prefix = "Untranslated strings:\n"), problems.isEmpty())
  }
}
