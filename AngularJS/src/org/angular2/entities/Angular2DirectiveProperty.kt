// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.angular2.entities

import com.intellij.javascript.web.webTypes.js.WebTypesTypeScriptSymbolTypeSupport
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import org.angular2.entities.impl.TypeScriptElementDocumentationTarget
import org.angular2.lang.types.Angular2TypeUtils
import org.angular2.web.Angular2PsiSourcedSymbol
import org.angular2.web.Angular2WebSymbolsQueryConfigurator.Companion.KIND_NG_DIRECTIVE_OUTPUTS

interface Angular2DirectiveProperty : Angular2PsiSourcedSymbol, Angular2Element {

  override val name: String

  val rawJsType: JSType?

  override val source: PsiElement
    get() = sourceElement

  override val project: Project
    get() = sourceElement.project

  override val namespace: String
    get() = WebSymbol.NAMESPACE_JS

  override val priority: WebSymbol.Priority?
    get() = WebSymbol.Priority.LOW

  override val type: JSType?
    get() = if (kind == KIND_NG_DIRECTIVE_OUTPUTS) {
      Angular2TypeUtils.extractEventVariableType(rawJsType)
    }
    else {
      rawJsType
    }

  override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = if (WebTypesTypeScriptSymbolTypeSupport.isBoolean(type)) {
      WebSymbolHtmlAttributeValue.create(null, null, false, null, null)
    }
    else {
      null
    }

  override fun createPointer(): Pointer<out Angular2DirectiveProperty>

  override fun getDocumentationTarget(): DocumentationTarget {
    if (hasNonPrivateDocComment(sourceElement)) {
      return TypeScriptElementDocumentationTarget(name, sourceElement)
    }
    val clazz = PsiTreeUtil.getContextOfType(source, TypeScriptClass::class.java)
    return if (clazz != null) {
      TypeScriptElementDocumentationTarget(name, clazz)
    }
    else super.getDocumentationTarget()
  }

  companion object {

    fun hasNonPrivateDocComment(element: PsiElement): Boolean {
      val comment = JSDocumentationUtils.findDocComment(element)
      return comment is JSDocComment && comment.tags.none { tag -> "docs-private" == tag.name }
    }
  }
}
