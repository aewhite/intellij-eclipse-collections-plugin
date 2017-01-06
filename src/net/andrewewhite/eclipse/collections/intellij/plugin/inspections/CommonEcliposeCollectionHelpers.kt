package net.andrewewhite.eclipse.collections.intellij.plugin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * Created by awhite on 1/5/17.
 */

fun isEclipseCollectionsBeingUsed(holder: ProblemsHolder): Boolean {
    val manager = holder.manager
    val scope = GlobalSearchScope.allScope(holder.project)
    val javaPsiFacade = JavaPsiFacade.getInstance(manager.project)
    val richIterableClass = javaPsiFacade.findClass(CommonEclipseCollectionClassNames.EC_RICH_ITERABLE, scope)

    return richIterableClass != null
}