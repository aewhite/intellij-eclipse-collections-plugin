package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.JavaTokenType.DOT
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import org.eclipse.collections.api.RichIterable
import org.eclipse.collections.api.list.MutableList
import org.eclipse.collections.api.set.ImmutableSet
import org.eclipse.collections.impl.factory.Lists
import org.eclipse.collections.impl.factory.Sets
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression
import javax.swing.JComponent

/**
 * Created by awhite on 1/3/17.
 */
class EclipseCollectionsNeedlessIntermediateCollectionsInspection : BaseJavaBatchLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!isEclipseCollectionsBeingUsed(holder)) { return PsiElementVisitor.EMPTY_VISITOR }
        return EclipseCollectionsNeedlessIntermediateCollectionsInspection.Visitor(holder, isOnTheFly)
    }

    internal class Visitor(val holder: ProblemsHolder, val isOnTheFly: Boolean) : JavaElementVisitor() {
        val elementFactory: PsiElementFactory = JavaPsiFacade.getElementFactory(holder.project)
        val richIterableType: PsiClassType = elementFactory.createTypeByFQClassName(CommonEclipseCollectionClassNames.EC_RICH_ITERABLE)
        val lazyIterableType: PsiClassType = elementFactory.createTypeByFQClassName(CommonEclipseCollectionClassNames.EC_LAZY_ITERABLE)
        val lazyClass: PsiClass = lazyIterableType.resolve()!!
        val lazyMethodsNames: ImmutableSet<String> = findInterestingLazyMethod()
        val allLazyMethodsNames: ImmutableSet<String> = Sets.immutable.of(*lazyClass.allMethods).collect { it.name }
        val seenMethods = Sets.mutable.empty<PsiMethodCallExpression>()

        // TODO handle primitive lazy iterables
        private fun findInterestingLazyMethod() = Sets.immutable.of(*lazyClass.methods)
                .collectIf(
                        { it.returnType?.isAssignableFrom(lazyIterableType) ?: false },
                        { it.name })

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            super.visitMethodCallExpression(expression)

            if (!isMethodCallInteresting(expression)) { return }

            val callChain = buildLazyCallChain(expression)

            if (!isCallChainInteresting(callChain)) { return }
            if (expression in seenMethods) { return }
            
            seenMethods.addAll(callChain)
            holder.registerProblem(
                    expression,
                    "Should use asLazy to avoid intermediate collections",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange.create(0, callChain.last().textRange.endOffset - callChain.first().textRange.startOffset),
                    EclipseCollectionAsLazyQuickFix())
        }

        private fun isCallChainInteresting(callChain: MutableList<PsiMethodCallExpression>) = callChain.size >= 2 && callChain.allSatisfy { it.methodExpression.referenceName in allLazyMethodsNames }

        private fun isMethodCallInteresting(expression: PsiMethodCallExpression): Boolean {
            when {
                !isMethodNameCandidateForAsLazy(expression) -> return false
                !isTypeCandidateForAsLazy(expression) -> return false
            }

            return doesLazyIterableSupportMethod(expression)
        }

        private fun doesLazyIterableSupportMethod(expression: PsiMethodCallExpression): Boolean {
            val method = expression.resolveMethod() ?: return true
            val containingClass = method.containingClass ?: return true

            if (!supportsAsLazy(containingClass)) {
                return false
            }

            return true
        }

        private fun isTypeCandidateForAsLazy(expression: PsiMethodCallExpression): Boolean {
            val type = expression.type ?: return false

            when {
                !richIterableType.isAssignableFrom(type) -> return false
                lazyIterableType.isAssignableFrom(type) -> return false
                else -> return true
            }
        }

        private fun isMethodNameCandidateForAsLazy(expression: PsiMethodCallExpression): Boolean {
            val methodReferenceName = expression.methodExpression.referenceName ?: return true

            return methodReferenceName in lazyMethodsNames
        }

        private fun supportsAsLazy(containingClass: PsiClass) = containingClass.findMethodsByName("asLazy", true).isNotEmpty()

        private fun buildLazyCallChain(expression: PsiMethodCallExpression): MutableList<PsiMethodCallExpression> {
            val methodCallExpressions = Lists.mutable.empty<PsiMethodCallExpression>()
            var currentExpression = expression

            while (true) {
                methodCallExpressions.add(currentExpression)

                val parent = currentExpression.parent ?: break
                val grandParent = parent.parent ?: break

                if (parent is PsiReferenceExpression &&
                    grandParent is PsiMethodCallExpression &&
                    grandParent.methodExpression.referenceName in allLazyMethodsNames ) {
                    // method calls should have a parent that defines the actual reference to the method; note that a
                    // method call is a reference to a method plus the parameters
                    currentExpression = grandParent
                }
                else {
                    break
                }
            }

            return methodCallExpressions
        }
    }
}

class EclipseCollectionAsLazyQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Use asLazy"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element: PsiMethodCallExpression = descriptor.getPsiElement() as PsiMethodCallExpression
        val elementFactory = JavaPsiFacade.getElementFactory(element.getProject())

        val referenceExpression = element.firstChild as PsiReferenceExpression
        val sourceExpression = referenceExpression.firstChild
        sourceExpression.replace(elementFactory.createExpressionFromText(sourceExpression.text + ".asLazy()", sourceExpression))
        CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(element))
    }
}
