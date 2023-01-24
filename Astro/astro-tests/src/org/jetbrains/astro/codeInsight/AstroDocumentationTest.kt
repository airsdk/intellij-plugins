package org.jetbrains.astro.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.webSymbols.checkDocumentationAtCaret
import com.intellij.webSymbols.checkLookupElementDocumentationAtCaret
import org.jetbrains.astro.AstroCodeInsightTestCase

class AstroDocumentationTest : AstroCodeInsightTestCase() {

  fun testHtmlTag() = doTest()

  fun testHtmlAttribute() = doTest()

  //region Test configuration and helper methods
  override fun getBasePath(): String {
    return "codeInsight/documentation"
  }

  private fun doTest() {
    myFixture.configureByFile("${getTestName(true)}.astro")
    myFixture.checkDocumentationAtCaret()
  }

  private fun doLookupTest(
    lookupFilter: (item: LookupElement) -> Boolean = { true }
  ) {
    myFixture.configureByFile("${getTestName(true)}.astro")
    myFixture.checkLookupElementDocumentationAtCaret(renderPriority = true, renderTypeText = true, lookupFilter = lookupFilter)
  }
  //endregion

}