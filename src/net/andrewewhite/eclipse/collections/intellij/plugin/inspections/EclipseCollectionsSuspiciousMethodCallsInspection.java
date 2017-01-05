package net.andrewewhite.eclipse.collections.intellij.plugin.inspections;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * NOTE: This class is based heavily off of IntelliJ's SuspiciousCollectionsMethodCallsInspection but changed to support
 *       Eclipse Collections style Iterables. The coding style and any oddities are left as-is in order to easily patch
 *       future versions based on up-stream changes
 *
 */
public class EclipseCollectionsSuspiciousMethodCallsInspection extends BaseJavaBatchLocalInspectionTool {
    public boolean REPORT_CONVERTIBLE_METHOD_CALLS = true;


    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
        final List<PsiMethod> patternMethods = new ArrayList<>();
        final IntArrayList indices = new IntArrayList();
        return new JavaElementVisitor() {
            @Override
            public void visitReferenceExpression(final PsiReferenceExpression expression) {
                visitExpression(expression);
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
                super.visitMethodCallExpression(methodCall);
                final String message = getSuspiciousMethodCallMessage(methodCall,
                                                                      REPORT_CONVERTIBLE_METHOD_CALLS,
                                                                      patternMethods,
                                                                      indices
                );
                if (message != null) {
                    holder.registerProblem(methodCall.getArgumentList().getExpressions()[0], message);
                }
            }
        };
    }

    @Nullable
    private static String getSuspiciousMethodCallMessage(final PsiMethodCallExpression methodCall,
                                                         final boolean reportConvertibleMethodCalls,
                                                         final List<PsiMethod> patternMethods,
                                                         final IntArrayList indices) {
        final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1) { return null; }

        PsiType argType = args[0].getType();
        final String plainMessage = EclipseCollectionsSuspiciousMethodCallUtil
                .getSuspiciousMethodCallMessage(methodCall,
                                                args[0],
                                                argType,
                                                reportConvertibleMethodCalls,
                                                patternMethods,
                                                indices);
        if (plainMessage != null) {
            final PsiType dfaType = GuessManager.getInstance(methodCall.getProject())
                                                .getControlFlowExpressionType(args[0]);
            if (dfaType != null && EclipseCollectionsSuspiciousMethodCallUtil
                                           .getSuspiciousMethodCallMessage(methodCall,
                                                                           args[0],
                                                                           dfaType,
                                                                           reportConvertibleMethodCalls,
                                                                           patternMethods,
                                                                           indices) == null) {
                return null;
            }
        }

        return plainMessage;
    }

}
