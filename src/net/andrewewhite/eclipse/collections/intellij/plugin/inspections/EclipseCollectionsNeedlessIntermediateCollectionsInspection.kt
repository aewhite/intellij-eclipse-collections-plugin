package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.JavaTokenType.DOT
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
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
        return EclipseCollectionsNeedlessIntermediateCollectionsInspection.Visitor(holder, isOnTheFly)
    }

    internal class Visitor(val holder: ProblemsHolder, val isOnTheFly: Boolean) : JavaElementVisitor() {
        val elementFactory: PsiElementFactory = JavaPsiFacade.getElementFactory(holder.project)
        val richIterableType: PsiClassType = elementFactory.createTypeByFQClassName("org.eclipse.collections.api.RichIterable")
        val lazyIterableType: PsiClassType = elementFactory.createTypeByFQClassName("org.eclipse.collections.api.LazyIterable")
        val lazyClass: PsiClass = lazyIterableType.resolve()!!
        val lazyMethodsNames: ImmutableSet<String> = findInterestingLazyMethod()
        val allLazyMethodsNames: ImmutableSet<String> = Sets.immutable.of(*lazyClass.allMethods).collect { it.name }

        // TODO handle primitive lazy iterables
        private fun findInterestingLazyMethod() = Sets.immutable.of(*lazyClass.methods)
                .collectIf(
                        { it.returnType?.isAssignableFrom(lazyIterableType) ?: false },
                        { it.name })

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            super.visitMethodCallExpression(expression)

            val methodReferenceName = expression.methodExpression.referenceName ?: return
            if (methodReferenceName !in lazyMethodsNames) { return }

            val type = expression.type ?: return
            if (!richIterableType.isAssignableFrom(type)) { return }
            if (lazyIterableType.isAssignableFrom(type))  { return }

            val method = expression.resolveMethod() ?: return
            val containingClass = method.containingClass ?: return

            if (!supportsAsLazy(containingClass)) { return }

            val callChain = buildLazyCallChain(expression)
            if (callChain.size < 2) { return }

            if (!callChain.allSatisfy { it.methodExpression.referenceName in allLazyMethodsNames }) { return }

            val fixes = arrayOf(EclipseCollectionAsLazyQuickFix())
            holder.registerProblem(
                    expression,
                    "Should use asLazy to avoid intermediate collections",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange.create(0, callChain.last().textRange.endOffset - callChain.first().textRange.startOffset),
                    *fixes)
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
