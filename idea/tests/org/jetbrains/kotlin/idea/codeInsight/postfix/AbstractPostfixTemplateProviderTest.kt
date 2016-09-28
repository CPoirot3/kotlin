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

package org.jetbrains.kotlin.idea.codeInsight.postfix

import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.KotlinTestUtils


abstract class AbstractPostfixTemplateProviderTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    override fun getTestDataPath() = KotlinTestUtils.getHomeDirectory()

    protected fun doTest(fileName: String) {
        myFixture.configureByFile(fileName)
        myFixture.type("\t")
        myFixture.checkResultByFile(fileName + ".after")

        val previouslySuggestedExpressions = KtPostfixTemplateProvider.previouslySuggestedExpressions
        if (previouslySuggestedExpressions.size > 1) {
            fail("Only one expression should be suggested, but $previouslySuggestedExpressions were found")
        }
    }

    override fun tearDown() {
        super.tearDown()
        KtPostfixTemplateProvider.previouslySuggestedExpressions = emptyList()
    }
}
