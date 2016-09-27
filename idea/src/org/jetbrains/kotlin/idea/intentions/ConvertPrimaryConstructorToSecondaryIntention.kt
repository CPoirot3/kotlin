/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiFactory.CallableBuilder
import org.jetbrains.kotlin.psi.KtPsiFactory.CallableBuilder.Target.CONSTRUCTOR
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class ConvertPrimaryConstructorToSecondaryIntention : SelfTargetingIntention<KtPrimaryConstructor>(
        KtPrimaryConstructor::class.java,
        "Convert to secondary constructor"
) {
    override fun isApplicableTo(element: KtPrimaryConstructor, caretOffset: Int) =
            element.containingClassOrObject is KtClass && element.valueParameters.all { !it.hasValOrVar() || it.annotationEntries.isEmpty() }

    override fun applyTo(element: KtPrimaryConstructor, editor: Editor?) {
        val klass = element.containingClassOrObject as? KtClass ?: return
        klass.getAnonymousInitializers()
        val factory = KtPsiFactory(klass)
        val initializerMap = mutableMapOf<KtProperty, String>()
        for (property in klass.getProperties()) {
            if (property.typeReference == null) {
                SpecifyTypeExplicitlyIntention().applyTo(property, editor)
            }
            val initializer = property.initializer ?: continue
            initializerMap[property] = initializer.text
            initializer.delete()
            property.equalsToken!!.delete()
        }
        val constructor = factory.createSecondaryConstructor(
                CallableBuilder(CONSTRUCTOR).apply {
                    element.modifierList?.let { modifier(it.text) }
                    typeParams()
                    name("constructor")
                    for (valueParameter in element.valueParameters) {
                        val annotations = valueParameter.annotationEntries.joinToString(separator = " ") { it.text }
                        val vararg = if (valueParameter.isVarArg) "vararg" else ""
                        param("$annotations $vararg ${valueParameter.name!!}",
                              valueParameter.typeReference!!.text, valueParameter.defaultValue?.text)
                    }
                    noReturnType()
                    for (superTypeEntry in klass.getSuperTypeListEntries()) {
                        if (superTypeEntry is KtSuperTypeCallEntry) {
                            superDelegation(superTypeEntry.valueArgumentList?.text ?: "")
                            superTypeEntry.valueArgumentList?.delete()
                        }
                    }
                    if (element.valueParameters.firstOrNull { it.hasValOrVar() } != null || initializerMap.isNotEmpty()) {
                        val valueParameterInitializers = element.valueParameters.filter { it.hasValOrVar() }.joinToString(separator = "\n") {
                            val name = it.name!!
                            "this.$name = $name"
                        }
                        val classBodyInitializers = klass.declarations.filter {
                            (it is KtProperty && initializerMap[it] != null) || it is KtAnonymousInitializer
                        }.joinToString(separator = "\n") {
                            if (it is KtProperty) {
                                val name = it.name!!
                                val text = initializerMap[it]
                                if (text != null) {
                                    "this.$name = $text"
                                }
                                else {
                                    ""
                                }
                            }
                            else {
                                ((it as KtAnonymousInitializer).body as? KtBlockExpression)?.statements?.joinToString(separator = "\n") {
                                    it.text
                                } ?: ""
                            }
                        }
                        blockBody(listOf(valueParameterInitializers, classBodyInitializers)
                                          .filter(String::isNotEmpty).joinToString(separator = "\n"))
                    }
                }.asString()
        )
        klass.addDeclarationBefore(constructor, null)
        for (valueParameter in element.valueParameters.reversed()) {
            if (!valueParameter.hasValOrVar()) continue
            val modifiers = valueParameter.modifierList?.text?.replace("vararg", "")
            val property = factory.createProperty(modifiers, valueParameter.name!!,
                                                  valueParameter.typeReference?.text,
                                                  valueParameter.isMutable, null)
            klass.addDeclarationBefore(property, null)
        }
        for (anonymousInitializer in klass.getAnonymousInitializers()) {
            anonymousInitializer.delete()
        }
        element.delete()
    }
}