package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.eclipse.collections.api.set.ImmutableSet
import org.eclipse.collections.impl.factory.Sets
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList

/**
 * Created by awhite on 1/5/17.
 */
class ConvertToEclipseCollectionInspection : BaseJavaBatchLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!isEclipseCollectionsBeingUsed(holder)) { return PsiElementVisitor.EMPTY_VISITOR }
        return ConvertToEclipseCollectionInspection.Visitor(holder, isOnTheFly)
    }

    class Visitor(val holder: ProblemsHolder, val onTheFly: Boolean) : JavaElementVisitor() {
        companion object {
            val INTERESTING_CLASSES: ImmutableSet<String> = Sets.immutable.of(
                    CommonClassNames.JAVA_UTIL_ARRAY_LIST,
                    CommonClassNames.JAVA_UTIL_HASH_SET,
                    CommonClassNames.JAVA_UTIL_HASH_MAP)
        }

        override fun visitNewExpression(expression: PsiNewExpression) {
            super.visitNewExpression(expression)
            val type = expression.type as? PsiClassType ?: return
            if (!isInterestingType(type)) { return }

            val classReference = expression.classReference ?: return
            val typeParameters = classReference.typeParameters
            if (typeParameters.isEmpty()) { return }

            val suggestedReplacement =  suggestAlternative(type, typeParameters) ?: return

            holder.registerProblem(
                    expression,
                    "Could use '${suggestedReplacement}' from Eclipse Collections",
                    ProblemHighlightType.WEAK_WARNING,
                    null as TextRange?)
        }

        fun suggestAlternative(type: PsiClassType, typeParameters: Array<out PsiType>): String? {
            return when (type.rawType().canonicalText) {
                CommonClassNames.JAVA_UTIL_ARRAY_LIST -> suggestArrayListAlternative(typeParameters)
                CommonClassNames.JAVA_UTIL_HASH_SET -> suggestHashSetAlternative(typeParameters)
                CommonClassNames.JAVA_UTIL_HASH_MAP -> suggestHashMapAlternative(typeParameters)
                else -> null
            }
        }

        fun suggestArrayListAlternative(typeParameters: Array<out PsiType>): String? {
            if (typeParameters.size != 1) { return null}
            val genericType = typeParameters[0] as? PsiClassType ?: return null
            val primitivePrefix = tryResolvePrimitiveImplPrefix(genericType)

            return when (primitivePrefix) {
                null -> "FastList<${genericType.presentableText}>"
                else -> primitivePrefix + "ArrayList"
            }
        }

        private fun  suggestHashSetAlternative(typeParameters: Array<out PsiType>): String? {
            if (typeParameters.size != 1) { return null}
            val genericType = typeParameters[0] as? PsiClassType ?: return null
            val primitivePrefix = tryResolvePrimitiveImplPrefix(genericType)

            return when (primitivePrefix) {
                null -> "UnifiedSet<${genericType.presentableText}>"
                else -> primitivePrefix + "HashSet"
            }
        }

        private fun  suggestHashMapAlternative(typeParameters: Array<out PsiType>): String? {
            if (typeParameters.size != 2) { return null}
            val genericKeyType = typeParameters[0] as? PsiClassType ?: return null
            val genericValueType = typeParameters[1] as? PsiClassType ?: return null
            val primitiveKeyPrefix = tryResolvePrimitiveImplPrefix(genericKeyType)
            val primitiveValuePrefix = tryResolvePrimitiveImplPrefix(genericValueType)

            //TODO: make suggestions for multimaps

            if (primitiveKeyPrefix == null && primitiveValuePrefix == null) {
                return "UnifiedHash<${genericKeyType.presentableText}, ${genericValueType.presentableText}>"
            }

            return "${primitiveKeyPrefix ?: "Object"}${primitiveValuePrefix ?: "Object"}HashMap"
        }

        fun tryResolvePrimitiveImplPrefix(genericType: PsiClassType): String? {
            return when (genericType.rawType().canonicalText) {
                CommonClassNames.JAVA_LANG_FLOAT -> "Float"
                CommonClassNames.JAVA_LANG_DOUBLE -> "Double"
                CommonClassNames.JAVA_LANG_BYTE -> "Byte"
                CommonClassNames.JAVA_LANG_CHARACTER -> "Char"
                CommonClassNames.JAVA_LANG_SHORT -> "Short"
                CommonClassNames.JAVA_LANG_INTEGER -> "Int"
                CommonClassNames.JAVA_LANG_LONG -> "Long"
                else -> return null
            }
        }

        private fun isInterestingType(type: PsiClassType): Boolean {
            return type.rawType().canonicalText in INTERESTING_CLASSES
        }
    }
}