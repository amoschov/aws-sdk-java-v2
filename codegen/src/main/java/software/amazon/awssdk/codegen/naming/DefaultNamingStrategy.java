/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen.naming;

import static java.util.stream.Collectors.joining;
import static software.amazon.awssdk.codegen.internal.Constant.AUTHORIZER_NAME_PREFIX;
import static software.amazon.awssdk.codegen.internal.Constant.CONFLICTING_NAME_SUFFIX;
import static software.amazon.awssdk.codegen.internal.Constant.EXCEPTION_CLASS_SUFFIX;
import static software.amazon.awssdk.codegen.internal.Constant.FAULT_CLASS_SUFFIX;
import static software.amazon.awssdk.codegen.internal.Constant.REQUEST_CLASS_SUFFIX;
import static software.amazon.awssdk.codegen.internal.Constant.RESPONSE_CLASS_SUFFIX;
import static software.amazon.awssdk.codegen.internal.Utils.unCapitalize;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import software.amazon.awssdk.codegen.internal.Constant;
import software.amazon.awssdk.codegen.internal.Utils;
import software.amazon.awssdk.codegen.model.config.customization.CustomizationConfig;
import software.amazon.awssdk.codegen.model.intermediate.MemberModel;
import software.amazon.awssdk.codegen.model.service.ServiceModel;
import software.amazon.awssdk.codegen.model.service.Shape;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Default implementation of naming strategy respecting.
 */
public class DefaultNamingStrategy implements NamingStrategy {

    private static Logger log = Logger.loggerFor(DefaultNamingStrategy.class);

    private static final Set<String> RESERVED_KEYWORDS;

    private static final Set<String> RESERVED_EXCEPTION_METHOD_NAMES;

    private static final Set<Object> RESERVED_STRUCTURE_METHOD_NAMES;

    static {
        Set<String> reservedJavaKeywords = new HashSet<>();
        Collections.addAll(reservedJavaKeywords,
                           "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                           "const", "continue", "default", "do", "double", "else", "enum", "extends", "false", "final",
                           "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                           "interface", "long", "native", "new", "null", "package", "private", "protected", "public",
                           "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
                           "throws", "transient", "true", "try", "void", "volatile", "while");
        RESERVED_KEYWORDS = Collections.unmodifiableSet(reservedJavaKeywords);


        Set<String> reservedJavaMethodNames = new HashSet<>();
        Collections.addAll(reservedJavaMethodNames,
                           "equals", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

        Set<String> reserveJavaPojoMethodNames = new HashSet<>(reservedJavaMethodNames);
        Collections.addAll(reserveJavaPojoMethodNames,
                           "builder", "sdkFields", "toBuilder");

        Set<String> reservedExceptionMethodNames = new HashSet<>(reserveJavaPojoMethodNames);
        Collections.addAll(reservedExceptionMethodNames,
                           "awsErrorDetails", "cause", "fillInStackTrace", "getCause", "getLocalizedMessage",
                           "getMessage", "getStackTrace", "getSuppressed", "isClockSkewException", "isThrottlingException",
                           "printStackTrace", "requestId", "retryable", "serializableBuilderClass", "statusCode");
        RESERVED_EXCEPTION_METHOD_NAMES = Collections.unmodifiableSet(reservedExceptionMethodNames);

        Set<String> reservedStructureMethodNames = new HashSet<>(reserveJavaPojoMethodNames);
        Collections.addAll(reservedStructureMethodNames,
                           "overrideConfiguration", "sdkHttpResponse");
        RESERVED_STRUCTURE_METHOD_NAMES = Collections.unmodifiableSet(reservedStructureMethodNames);
    }

    private final ServiceModel serviceModel;
    private final CustomizationConfig customizationConfig;

    public DefaultNamingStrategy(ServiceModel serviceModel,
                                 CustomizationConfig customizationConfig) {
        this.serviceModel = serviceModel;
        this.customizationConfig = customizationConfig;
    }

    private static boolean isJavaKeyword(String word) {
        return RESERVED_KEYWORDS.contains(word) ||
               RESERVED_KEYWORDS.contains(StringUtils.lowerCase(word));
    }

    @Override
    public String getServiceName() {
        String baseName = Stream.of(serviceModel.getMetadata().getServiceId())
                                .filter(Objects::nonNull)
                                .filter(s -> !s.trim().isEmpty())
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("ServiceId is missing in the c2j model."));

        baseName = pascalCase(baseName);

        // Special cases
        baseName = Utils.removeLeading(baseName, "Amazon");
        baseName = Utils.removeLeading(baseName, "Aws");
        baseName = Utils.removeTrailing(baseName, "Service");

        return baseName;
    }

    @Override
    public String getClientPackageName(String serviceName) {
        return getCustomizedPackageName(concatServiceNameIfShareModel(serviceName),
                                        Constant.PACKAGE_NAME_CLIENT_PATTERN);
    }

    @Override
    public String getModelPackageName(String serviceName) {
        // Share model package classes if we are sharing models.
        if (customizationConfig.getShareModelConfig() != null
            && customizationConfig.getShareModelConfig().getShareModelWith() != null) {
            serviceName = customizationConfig.getShareModelConfig().getShareModelWith();
        }
        return getCustomizedPackageName(serviceName,
                                        Constant.PACKAGE_NAME_MODEL_PATTERN);
    }

    @Override
    public String getTransformPackageName(String serviceName) {
        // Share transform package classes if we are sharing models.
        if (customizationConfig.getShareModelConfig() != null
            && customizationConfig.getShareModelConfig().getShareModelWith() != null) {
            serviceName = customizationConfig.getShareModelConfig().getShareModelWith();
        }
        return getCustomizedPackageName(serviceName,
                                        Constant.PACKAGE_NAME_TRANSFORM_PATTERN);
    }

    @Override
    public String getRequestTransformPackageName(String serviceName) {
        return getCustomizedPackageName(concatServiceNameIfShareModel(serviceName),
                                        Constant.PACKAGE_NAME_TRANSFORM_PATTERN);
    }

    @Override
    public String getPaginatorsPackageName(String serviceName) {
        return getCustomizedPackageName(concatServiceNameIfShareModel(serviceName), Constant.PACKAGE_NAME_PAGINATORS_PATTERN);
    }

    @Override
    public String getSmokeTestPackageName(String serviceName) {

        return getCustomizedPackageName(concatServiceNameIfShareModel(serviceName),
                                        Constant.PACKAGE_NAME_SMOKE_TEST_PATTERN);
    }

    /**
     * If the service is sharing models with other services, we need to concatenate its customized package name
     * if provided or service name with the shared service name.
     */
    private String concatServiceNameIfShareModel(String serviceName) {

        if (customizationConfig.getShareModelConfig() != null) {
            return customizationConfig.getShareModelConfig().getShareModelWith() + "." +
                   Optional.ofNullable(customizationConfig.getShareModelConfig().getPackageName()).orElse(serviceName);
        }

        return serviceName;
    }

    private String screamCase(String word) {
        return Stream.of(splitOnWordBoundaries(word)).map(s -> s.toUpperCase(Locale.US)).collect(joining("_"));
    }

    private String pascalCase(String word) {
        return Stream.of(splitOnWordBoundaries(word)).map(StringUtils::lowerCase).map(Utils::capitalize).collect(joining());
    }

    private String getCustomizedPackageName(String serviceName, String defaultPattern) {
        return String.format(defaultPattern, StringUtils.lowerCase(serviceName));
    }

    @Override
    public String getExceptionName(String errorShapeName) {
        if (errorShapeName.endsWith(FAULT_CLASS_SUFFIX)) {
            return pascalCase(errorShapeName.substring(0, errorShapeName.length() - FAULT_CLASS_SUFFIX.length())) +
                              EXCEPTION_CLASS_SUFFIX;
        } else if (errorShapeName.endsWith(EXCEPTION_CLASS_SUFFIX)) {
            return pascalCase(errorShapeName);
        }
        return pascalCase(errorShapeName) + EXCEPTION_CLASS_SUFFIX;
    }

    @Override
    public String getRequestClassName(String operationName) {
        return pascalCase(operationName) + REQUEST_CLASS_SUFFIX;
    }

    @Override
    public String getResponseClassName(String operationName) {
        return pascalCase(operationName) + RESPONSE_CLASS_SUFFIX;
    }

    @Override
    public String getVariableName(String name) {
        // Exclude keywords because they will not compile, and exclude reserved method names because they're frequently
        // used for local variable names.
        if (RESERVED_KEYWORDS.contains(name) ||
            RESERVED_STRUCTURE_METHOD_NAMES.contains(name) ||
            RESERVED_EXCEPTION_METHOD_NAMES.contains(name)) {
            return unCapitalize(name + CONFLICTING_NAME_SUFFIX);
        }

        return unCapitalize(name);
    }

    @Override
    public String getEnumValueName(String enumValue) {
        String result = enumValue;

        // Special cases
        result = result.replaceAll("textORcsv", "TEXT_OR_CSV");

        // Split into words
        result = String.join("_", splitOnWordBoundaries(result));

        // Enums should be upper-case
        result = StringUtils.upperCase(result);

        if (!result.matches("^[A-Z][A-Z0-9_]*$")) {
            String attempt = result;
            log.warn(() -> "Invalid enum member generated for input '" + enumValue + "'. Best attempt: '" + attempt + "' If this "
                           + "enum is not customized out, the build will fail.");
        }

        return result;
    }

    @Override
    public String getJavaClassName(String shapeName) {
        return Arrays.stream(shapeName.split("[._-]|\\W"))
                     .filter(s -> !StringUtils.isEmpty(s))
                     .map(Utils::capitalize)
                     .collect(joining());
    }

    @Override
    public String getAuthorizerClassName(String shapeName) {
        String converted = getJavaClassName(shapeName);
        if (converted.length() > 0 && !Character.isLetter(converted.charAt(0))) {
            return AUTHORIZER_NAME_PREFIX + converted;
        }
        return converted;
    }

    @Override
    public String getFluentGetterMethodName(String memberName, Shape parentShape, Shape shape) {
        String getterMethodName = Utils.unCapitalize(memberName);

        getterMethodName = rewriteInvalidMemberName(getterMethodName, parentShape);

        if (Utils.isOrContainsEnumShape(shape, serviceModel.getShapes())) {
            getterMethodName += "AsString";

            if (Utils.isListShape(shape) || Utils.isMapShape(shape)) {
                getterMethodName += "s";
            }
        }

        return getterMethodName;
    }

    @Override
    public String getFluentEnumGetterMethodName(String memberName, Shape parentShape, Shape shape) {
        if (!Utils.isOrContainsEnumShape(shape, serviceModel.getShapes())) {
            return null;
        }

        String getterMethodName = Utils.unCapitalize(memberName);
        getterMethodName = rewriteInvalidMemberName(getterMethodName, parentShape);
        return getterMethodName;
    }

    @Override
    public String getBeanStyleGetterMethodName(String memberName, Shape parentShape, Shape c2jShape) {
        String fluentGetterMethodName = getFluentGetterMethodName(memberName, parentShape, c2jShape);
        return String.format("get%s", Utils.capitalize(fluentGetterMethodName));
    }

    @Override
    public String getBeanStyleSetterMethodName(String memberName, Shape parentShape, Shape c2jShape) {
        String fluentSetterMethodName = getFluentSetterMethodName(memberName, parentShape, c2jShape);
        return String.format("set%s", Utils.capitalize(fluentSetterMethodName));
    }

    @Override
    public String getFluentSetterMethodName(String memberName, Shape parentShape, Shape shape) {
        String setterMethodName = Utils.unCapitalize(memberName);

        setterMethodName = rewriteInvalidMemberName(setterMethodName, parentShape);

        if (Utils.isOrContainsEnumShape(shape, serviceModel.getShapes()) &&
            (Utils.isListShape(shape) || Utils.isMapShape(shape))) {

            setterMethodName += "WithStrings";
        }

        return setterMethodName;
    }

    @Override
    public String getFluentEnumSetterMethodName(String memberName, Shape parentShape, Shape shape) {
        if (!Utils.isOrContainsEnumShape(shape, serviceModel.getShapes())) {
            return null;
        }

        String setterMethodName = Utils.unCapitalize(memberName);
        setterMethodName = rewriteInvalidMemberName(setterMethodName, parentShape);
        return setterMethodName;
    }

    @Override
    public String getSdkFieldFieldName(MemberModel memberModel) {
        return screamCase(memberModel.getName()) + "_FIELD";
    }

    private String rewriteInvalidMemberName(String memberName, Shape parentShape) {
        if (isJavaKeyword(memberName) || isDisallowedNameForShape(memberName, parentShape)) {
            return Utils.unCapitalize(memberName + CONFLICTING_NAME_SUFFIX);
        }

        return memberName;
    }

    private boolean isDisallowedNameForShape(String name, Shape parentShape) {
        if (parentShape.isException()) {
            return RESERVED_EXCEPTION_METHOD_NAMES.contains(name);
        } else {
            return RESERVED_STRUCTURE_METHOD_NAMES.contains(name);
        }
    }

    private String[] splitOnWordBoundaries(String toSplit) {
        String result = toSplit;

        // All non-alphanumeric characters are spaces
        result = result.replaceAll("[^A-Za-z0-9]+", " "); // acm-success -> "acm success"

        // If a number has a standalone v in front of it, separate it out (version).
        result = result.replaceAll("([^a-z]{2,})v([0-9]+)", "$1 v$2 ") // TESTv4 -> "TEST v4 "
                       .replaceAll("([^A-Z]{2,})V([0-9]+)", "$1 V$2 "); // TestV4 -> "Test V4 "

        // Add a space between camelCased words
        result = String.join(" ", result.split("(?<=[a-z])(?=[A-Z]([a-zA-Z]|[0-9]))")); // AcmSuccess -> "Acm Success"

        // Add a space after acronyms
        result = result.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2"); // ACMSuccess -> "ACM Success"

        // Add space after a number in the middle of a word
        result = result.replaceAll("([0-9])([a-zA-Z])", "$1 $2"); // s3ec2 -> "s3 ec2"

        // Remove extra spaces - multiple consecutive ones or those and the beginning/end of words
        result = result.replaceAll(" +", " ") // "Foo  Bar" -> "Foo Bar"
                       .trim(); // " Foo " -> Foo

        return result.split(" ");
    }

}
