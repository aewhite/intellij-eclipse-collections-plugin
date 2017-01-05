package net.andrewewhite.eclipse.collections.intellij.plugin.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * NOTE: This class is based heavily off of IntelliJ's SuspiciousMethodCallUtil but changed to support
 *       Eclipse Collections style Iterables. The coding style and any oddities are left as-is in order to easily patch
 *       future versions based on up-stream changes
 *
 */
public class EclipseCollectionsSuspiciousMethodCallUtil {
    static void setupPatternMethods(PsiManager manager,
                                    GlobalSearchScope searchScope,
                                    List<PsiMethod> patternMethods,
                                    IntArrayList indices) {
        final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(manager.getProject());
        final PsiClass richIterableClass = javaPsiFacade.findClass(CommonEclipseCollectionClassNames.EC_RICH_ITERABLE, searchScope);
        PsiType[] javaLangObject = {PsiType.getJavaLangObject(manager, searchScope)};

        if (richIterableClass != null) {
            MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod contains = MethodSignatureUtil.findMethodBySignature(richIterableClass, containsSignature, false);
            addMethod(contains, 0, patternMethods, indices);
        }

        final PsiClass orderedIterableClass = javaPsiFacade.findClass(CommonEclipseCollectionClassNames.EC_ORDERED_ITERABLE, searchScope);
        if (orderedIterableClass != null) {
            MethodSignature indexofSignature = MethodSignatureUtil.createMethodSignature("indexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod indexof = MethodSignatureUtil.findMethodBySignature(orderedIterableClass, indexofSignature, false);
            addMethod(indexof, 0, patternMethods, indices);
        }

        final PsiClass listIterableClass = javaPsiFacade.findClass(CommonEclipseCollectionClassNames.EC_LIST_ITERABLE, searchScope);
        if (listIterableClass != null) {
            MethodSignature lastindexofSignature = MethodSignatureUtil.createMethodSignature("lastIndexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod lastindexof = MethodSignatureUtil.findMethodBySignature(listIterableClass, lastindexofSignature, false);
            addMethod(lastindexof, 0, patternMethods, indices);
        }

        final PsiClass mapClass = javaPsiFacade.findClass(CommonEclipseCollectionClassNames.EC_MAP_ITERABLE, searchScope);
        if (mapClass != null) {
            MethodSignature getSignature = MethodSignatureUtil.createMethodSignature("get", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod get = MethodSignatureUtil.findMethodBySignature(mapClass, getSignature, false);
            addMethod(get, 0, patternMethods, indices);
            MethodSignature containsKeySignature = MethodSignatureUtil.createMethodSignature("containsKey", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod containsKey = MethodSignatureUtil.findMethodBySignature(mapClass, containsKeySignature, false);
            addMethod(containsKey, 0, patternMethods, indices);
            MethodSignature containsValueSignature = MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
            PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(mapClass, containsValueSignature, false);
            addMethod(containsValue, 1, patternMethods, indices);
        }
    }

    private static void addMethod(final PsiMethod patternMethod, int typeParamIndex, List<PsiMethod> patternMethods, IntArrayList indices) {
        if (patternMethod != null) {
            patternMethods.add(patternMethod);
            indices.add(typeParamIndex);
        }
    }

    static boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
        PsiClass aClass = inheritorCandidate.getContainingClass();
        PsiClass bClass = base.getContainingClass();
        if (aClass == null || bClass == null) return false;
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
        return substitutor != null &&
               MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
    }

    @Nullable
    public static String getSuspiciousMethodCallMessage(@NotNull PsiMethodCallExpression methodCall,
                                                        PsiExpression arg,
                                                        PsiType argType,
                                                        boolean reportConvertibleMethodCalls,
                                                        @NotNull List<PsiMethod> patternMethods,
                                                        @NotNull IntArrayList indices) {
        final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) return null;
        if (argType instanceof PsiPrimitiveType) {
            argType = ((PsiPrimitiveType)argType).getBoxedType(methodCall);
        }

        if (argType == null) return null;

        if (arg instanceof PsiConditionalExpression &&
            PsiPolyExpressionUtil.isPolyExpression(arg) &&
            argType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            return null;
        }

        final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
        PsiMethod calleeMethod = (PsiMethod)resolveResult.getElement();
        if (calleeMethod == null) return null;
        PsiMethod contextMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (patternMethods) {
            if (patternMethods.isEmpty()) {
                setupPatternMethods(methodCall.getManager(), methodCall.getResolveScope(), patternMethods, indices);
            }
        }

        for (int i = 0; i < patternMethods.size(); i++) {
            PsiMethod patternMethod = patternMethods.get(i);
            if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;
            int index = indices.get(i);

            //we are in collections method implementation
            if (contextMethod != null && isInheritorOrSelf(contextMethod, patternMethod)) return null;

            final PsiClass calleeClass = calleeMethod.getContainingClass();
            PsiSubstitutor substitutor = resolveResult.getSubstitutor();
            final PsiClass patternClass = patternMethod.getContainingClass();
            assert patternClass != null;
            assert calleeClass != null;
            substitutor = TypeConversionUtil.getClassSubstitutor(patternClass, calleeClass, substitutor);
            if (substitutor == null) continue;

            if (!patternMethod.getSignature(substitutor).equals(calleeMethod.getSignature(PsiSubstitutor.EMPTY))) continue;

            PsiTypeParameter[] typeParameters = patternClass.getTypeParameters();
            if (typeParameters.length <= index) return null;
            final PsiTypeParameter typeParameter = typeParameters[index];
            PsiType typeParamMapping = substitutor.substitute(typeParameter);
            if (typeParamMapping == null) return null;

            PsiParameter[] parameters = patternMethod.getParameterList().getParameters();
            if (parameters.length == 1 && "removeAll".equals(patternMethod.getName())) {
                PsiType paramType = parameters[0].getType();
                if (InheritanceUtil.isInheritor(paramType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                    PsiType qualifierType = qualifier.getType();
                    if (qualifierType != null) {
                        final PsiType itemType = JavaGenericsUtil.getCollectionItemType(argType, calleeMethod.getResolveScope());
                        final PsiType qualifierItemType = JavaGenericsUtil.getCollectionItemType(qualifierType, calleeMethod.getResolveScope());
                        if (qualifierItemType != null && itemType != null && !qualifierItemType.isAssignableFrom(itemType)) {
                            return InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                                             PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                                             PsiFormatUtil.formatType(itemType, 0, PsiSubstitutor.EMPTY));
                        }
                    }
                    return null;
                }
            }

            String message = null;
            if (typeParamMapping instanceof PsiCapturedWildcardType) {
                typeParamMapping = ((PsiCapturedWildcardType)typeParamMapping).getWildcard();
            }
            if (!typeParamMapping.isAssignableFrom(argType)) {
                if (typeParamMapping.isConvertibleFrom(argType)) {
                    if (reportConvertibleMethodCalls) {
                        message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor1",
                                                            PsiFormatUtil.formatMethod(calleeMethod, substitutor,
                                                                                       PsiFormatUtilBase.SHOW_NAME |
                                                                                       PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                                                       PsiFormatUtilBase.SHOW_TYPE));
                    }
                }
                else {
                    PsiType qualifierType = qualifier.getType();
                    if (qualifierType != null) {
                        message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                                            PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                                            PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY));
                    }
                }
            }
            return message;
        }
        return null;
    }
}