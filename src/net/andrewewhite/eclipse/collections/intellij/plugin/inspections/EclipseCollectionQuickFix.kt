package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager

/**
 * Created by awhite on 1/3/17.
 */
class EclipseCollectionQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Replace with Eclipse-Collections API equivalent"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.getPsiElement()
        val elementFactory = JavaPsiFacade.getElementFactory(element.getProject())
        val result = element.replace(elementFactory.createCommentFromText("// loop replaced", element))
        CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result))
    }

}