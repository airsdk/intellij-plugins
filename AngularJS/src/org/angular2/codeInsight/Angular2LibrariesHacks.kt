// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.codeInsight

import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.ecmascript6.resolve.JSFileReferencesUtil
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.modules.NodeModuleUtil
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSRecordType
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.lang.javascript.psi.types.JSAnyType
import com.intellij.lang.javascript.psi.types.JSCompositeTypeFactory
import com.intellij.lang.javascript.psi.types.JSGenericTypeImpl
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider.Result.create
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import org.angular2.entities.Angular2Directive
import org.angular2.entities.Angular2DirectiveProperty
import org.angular2.entities.ivy.Angular2IvyDirective
import org.angular2.entities.metadata.psi.Angular2MetadataDirectiveBase
import org.angular2.entities.metadata.psi.Angular2MetadataDirectiveProperty
import org.angular2.lang.Angular2LangUtil
import org.angular2.lang.Angular2LangUtil.ANGULAR_CORE_PACKAGE
import org.angular2.web.Angular2Symbol
import org.angular2.web.Angular2SymbolDelegate
import org.jetbrains.annotations.NonNls
import java.util.*

/**
 * This class is intended to be a single point of origin for any hack to support a badly written library.
 */
object Angular2LibrariesHacks {

  @NonNls
  private const val IONIC_ANGULAR_PACKAGE = "@ionic/angular"

  @NonNls
  private const val NG_MODEL_CHANGE = "ngModelChange"

  @NonNls
  private const val NG_FOR_OF = "ngForOf"

  @NonNls
  private const val NG_ITERABLE = "NgIterable"

  @NonNls
  private const val QUERY_LIST = "QueryList"

  /**
   * Hack for WEB-37879
   */
  @JvmStatic
  fun hackNgModelChangeType(type: JSType?, propertyName: String): JSType? {
    // Workaround issue with ngModelChange field.
    // The workaround won't execute once Angular source is corrected.
    return if (propertyName == NG_MODEL_CHANGE && type is JSRecordType && !type.hasProperties()) {
      JSAnyType.get(type.source)
    }
    else type
  }

  /**
   * Hack for WEB-37838
   */
  @JvmStatic
  fun hackIonicComponentOutputs(directive: Angular2Directive, outputs: MutableMap<String, String>) {
    if (!isIonicDirective(directive)) {
      return
    }
    val cls = directive.typeScriptClass ?: return
    // We can guess outputs by looking for fields with EventEmitter type
    cls.jsType.asRecordType().properties.forEach { prop ->
      try {
        val type = prop.asSafely<TypeScriptField>()?.jsType
        if (type != null && type.typeText.startsWith(Angular2LangUtil.EVENT_EMITTER)) {
          outputs[prop.memberName] = prop.memberName
        }
      }
      catch (ex: IllegalArgumentException) {
        //getTypeText may throw IllegalArgumentException - ignore it
      }
    }
  }

  /**
   * Hack for WEB-39722
   */
  @JvmStatic
  fun hackIonicComponentAttributeNames(directive: Angular2Directive): List<Angular2Symbol> {
    return if (!isIonicDirective(directive))
      emptyList()
    else directive.inputs.map { input -> IonicComponentAttribute(input) }
    // Add kebab case version of attribute - Ionic takes these directly from element bypassing Angular
  }

  private fun isIonicDirective(directive: Angular2Directive): Boolean {
    val containingNodePackage = if (directive is Angular2IvyDirective && directive.getName().startsWith("Ion")) {
      directive.typeScriptClass
        .let { PsiUtilCore.getVirtualFile(it) }
        ?.let { vf -> PackageJsonUtil.findUpPackageJson(vf) }
        ?.let { NodeModuleUtil.inferNodeModulePackageName(it) }
    }
    else
      (directive as? Angular2MetadataDirectiveBase<*>)
        ?.nodeModule
        ?.name
    return containingNodePackage == IONIC_ANGULAR_PACKAGE
  }

  /**
   * Hack for WEB-38825. Make ngForOf accept QueryList in addition to NgIterable
   */
  @JvmStatic
  fun hackQueryListTypeInNgForOf(type: JSType?,
                                 property: Angular2MetadataDirectiveProperty): JSType? {
    if (type is JSGenericTypeImpl && property.name == NG_FOR_OF) {
      val clazz: TypeScriptClass = PsiTreeUtil.getContextOfType(property.sourceElement, TypeScriptClass::class.java)
                                   ?: return type
      if (!type.typeText.contains(NG_ITERABLE))
        return type
      val queryListType: JSType = getQueryListType(clazz)
                                  ?: return type
      return JSCompositeTypeFactory.createUnionType(type.source, type, JSGenericTypeImpl(type.source, queryListType, type.arguments))
    }
    return type
  }

  private fun getQueryListType(scope: PsiElement): JSType? {
    return getCachedValue(scope) {
      for (module in JSFileReferencesUtil.resolveModuleReference(scope, ANGULAR_CORE_PACKAGE)) {
        if (module !is JSElement) continue
        val queryListClass = JSResolveResult.resolve(ES6PsiUtil.resolveSymbolInModule(QUERY_LIST, scope, module)) as? TypeScriptClass
        if (queryListClass != null && queryListClass.typeParameters.size == 1) {
          return@getCachedValue create<TypeScriptClass>(queryListClass, queryListClass, scope)
        }
      }
      create(null, PsiModificationTracker.MODIFICATION_COUNT)
    }?.jsType
  }

  private class IonicComponentAttribute(input: Angular2DirectiveProperty)
    : Angular2SymbolDelegate<Angular2DirectiveProperty>(input) {

    override val name: String = input.name.replace("([A-Z])".toRegex(), "-$1").lowercase(Locale.ENGLISH)

    override val namespace: String
      get() = WebSymbol.NAMESPACE_HTML

    override val kind: String
      get() = WebSymbol.KIND_HTML_ATTRIBUTES

    override fun createPointer(): Pointer<IonicComponentAttribute> {
      val input = this.delegate.createPointer()
      return Pointer {
        val newInput = input.dereference()
        if (newInput != null) IonicComponentAttribute(newInput) else null
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val attr = other as IonicComponentAttribute?
      return delegate == attr!!.delegate
    }

    override fun hashCode(): Int {
      return delegate.hashCode()
    }
  }

}
