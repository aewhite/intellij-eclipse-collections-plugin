package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInsight.ExceptionUtil
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

/**
 * Created by awhite on 1/3/17.
 */
class EclipseCollectionsApiMigrationInspection : BaseJavaBatchLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!PsiUtil.isLanguageLevel8OrHigher(holder.file)) {
            // technically EC conversions are valid for pre Java 8 but this use case is decreasing with time and are
            // not a priority at this time
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return EclipseCollectionsApiMigrationInspection.EclipseCollectionsApiMigrationVisitor(holder, isOnTheFly)
    }

    class EclipseCollectionsApiMigrationVisitor(private val holder: ProblemsHolder, private val isOnTheFly: Boolean) : JavaElementVisitor() {

        override fun visitForeachStatement(statement: PsiForeachStatement) {
            super.visitForeachStatement(statement)
            processLoop(statement)
        }

        override fun visitWhileStatement(statement: PsiWhileStatement) {
            super.visitWhileStatement(statement)
            processLoop(statement)
        }

        override fun visitForStatement(statement: PsiForStatement) {
            super.visitForStatement(statement)
            processLoop(statement)
        }

        private fun processLoop(statement: PsiLoopStatement) {
            val body = statement.body ?: return
            if (!ExceptionUtil.getThrownCheckedExceptions(body).isEmpty()) { return }



            val fixes = arrayOf(EclipseCollectionQuickFix())
            holder.registerProblem(
                    statement,
                    "Can be replaced with something",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    null as TextRange?,
                    *fixes)
        }
    }
}