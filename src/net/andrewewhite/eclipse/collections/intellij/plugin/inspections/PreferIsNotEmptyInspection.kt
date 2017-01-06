package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.JavaTokenType.EXCL
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager

/**
 * Created by awhite on 1/6/17.
 */

class PreferIsNotEmptyInspection: BaseJavaBatchLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!isEclipseCollectionsBeingUsed(holder)) { return PsiElementVisitor.EMPTY_VISITOR }
        return PreferIsNotEmptyInspection.Visitor(holder, isOnTheFly)
    }

    class Visitor(val holder: ProblemsHolder, val onTheFly: Boolean) : JavaElementVisitor() {
        override fun visitPrefixExpression(expression: PsiPrefixExpression) {
            super.visitPrefixExpression(expression)
            //TODO Add support for detecting cases like list.size() != 0 and list.size() > 0

            if (isNegationExpression(expression)) { return }

            val methodCall = expression.operand as? PsiMethodCallExpression ?: return
            val methodExpression = methodCall.methodExpression
            val sourceElement = methodExpression.firstChild ?: return

            if (methodExpression.referenceName != "isEmpty") { return }
            if (!classSupportsNotEmpty(methodCall)) return

            holder.registerProblem(
                    expression,
                    "Should use asLazy to avoid intermediate collections",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange.create(0, expression.textRange.length),
                    IsNotEmptyQuickFix(sourceElement))
        }

        private fun classSupportsNotEmpty(methodCall: PsiMethodCallExpression): Boolean {
            val resolvedMethodCall = methodCall.resolveMethod() ?: return true
            val containingClass = resolvedMethodCall.containingClass ?: return true

            if (containingClass.findMethodsByName("notEmpty", true).isEmpty()) {
                return false
            }

            return true
        }

        private fun isNegationExpression(expression: PsiPrefixExpression) = expression.operationTokenType != EXCL
    }
}

class IsNotEmptyQuickFix(val sourceElement: PsiElement) : LocalQuickFix {
    override fun getFamilyName(): String = "Use notEmpty"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.getPsiElement()
        val elementFactory = JavaPsiFacade.getElementFactory(element.getProject())
        element.replace(elementFactory.createExpressionFromText(sourceElement.text + ".notEmpty()", element))
        CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(element))
    }
}
