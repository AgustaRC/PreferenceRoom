/*
 * Copyright (C) 2017 skydoves
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.processor;

import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.annotation.NonNull;
import com.google.common.base.VerifyException;
import com.skydoves.preferenceroom.PreferenceRoom;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;

@SuppressWarnings("WeakerAccess")
public class PreferenceComponentGenerator {

  private final PreferenceComponentAnnotatedClass annotatedClazz;
  private final Map<String, PreferenceEntityAnnotatedClass> annotatedEntityMap;
  private final Elements annotatedElementUtils;

  private static final String CLAZZ_PREFIX = "PreferenceComponent_";
  private static final String ENTITY_PREFIX = "Preference_";
  private static final String FIELD_INSTANCE = "instance";
  private static final String CONSTRUCTOR_CONTEXT = "context";
  private static final String ENTITY_NAME_LIST = "EntityNameList";

  private static final String PACKAGE_CONTEXT = "android.content.Context";

  public PreferenceComponentGenerator(
      @NonNull PreferenceComponentAnnotatedClass annotatedClass,
      @NonNull Map<String, PreferenceEntityAnnotatedClass> annotatedEntityMap,
      @NonNull Elements elementUtils) {
    this.annotatedClazz = annotatedClass;
    this.annotatedEntityMap = annotatedEntityMap;
    this.annotatedElementUtils = elementUtils;
  }

  public TypeSpec generate() {
    return TypeSpec.classBuilder(getClazzName())
        .addJavadoc("Generated by PreferenceRoom. (https://github.com/skydoves/PreferenceRoom).\n")
        .addModifiers(PUBLIC)
        .addSuperinterface(annotatedClazz.typeName)
        .addField(getInstanceFieldSpec())
        .addFields(getEntityInstanceFieldSpecs())
        .addMethod(getConstructorSpec())
        .addMethod(getInitializeSpec())
        .addMethod(getInstanceSpec())
        .addMethods(getSuperMethodSpecs())
        .addMethods(getEntityInstanceSpecs())
        .addMethod(getEntityNameListSpec())
        .build();
  }

  private FieldSpec getInstanceFieldSpec() {
    return FieldSpec.builder(getClassType(), FIELD_INSTANCE, PRIVATE, STATIC).build();
  }

  private List<FieldSpec> getEntityInstanceFieldSpecs() {
    List<FieldSpec> fieldSpecs = new ArrayList<>();
    this.annotatedClazz.keyNames.forEach(
        keyName -> {
          FieldSpec instance =
              FieldSpec.builder(
                      getEntityClassType(annotatedEntityMap.get(keyName)),
                      getEntityInstanceFieldName(keyName),
                      PRIVATE,
                      STATIC)
                  .build();
          fieldSpecs.add(instance);
        });
    return fieldSpecs;
  }

  private MethodSpec getConstructorSpec() {
    MethodSpec.Builder builder =
        MethodSpec.constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameter(
                ParameterSpec.builder(getContextPackageType(), CONSTRUCTOR_CONTEXT)
                    .addAnnotation(NonNull.class)
                    .build());

    this.annotatedClazz.keyNames.forEach(
        keyName ->
            builder.addStatement(
                "$N = $N.getInstance($N.getApplicationContext())",
                getEntityInstanceFieldName(keyName),
                getEntityClazzName(annotatedEntityMap.get(keyName)),
                CONSTRUCTOR_CONTEXT));

    return builder.build();
  }

  private MethodSpec getInitializeSpec() {
    return MethodSpec.methodBuilder("init")
        .addModifiers(PUBLIC, STATIC)
        .addParameter(
            ParameterSpec.builder(getContextPackageType(), CONSTRUCTOR_CONTEXT)
                .addAnnotation(NonNull.class)
                .build())
        .addStatement("if($N != null) return $N", FIELD_INSTANCE, FIELD_INSTANCE)
        .addStatement("$N = new $N($N)", FIELD_INSTANCE, getClazzName(), CONSTRUCTOR_CONTEXT)
        .addStatement("return $N", FIELD_INSTANCE)
        .returns(getClassType())
        .build();
  }

  private MethodSpec getInstanceSpec() {
    return MethodSpec.methodBuilder("getInstance")
        .addModifiers(PUBLIC, STATIC)
        .addStatement("if($N != null) return $N", FIELD_INSTANCE, FIELD_INSTANCE)
        .addStatement("else throw new VerifyError(\"component is not initialized.\")")
        .returns(getClassType())
        .build();
  }

  private List<MethodSpec> getEntityInstanceSpecs() {
    List<MethodSpec> methodSpecs = new ArrayList<>();
    this.annotatedClazz.keyNames.forEach(
        keyName -> {
          String fieldName = getEntityInstanceFieldName(keyName);
          MethodSpec instance =
              MethodSpec.methodBuilder(StringUtils.toUpperCamel(keyName))
                  .addModifiers(PUBLIC)
                  .addStatement("return $N", fieldName)
                  .returns(getEntityClassType(annotatedEntityMap.get(keyName)))
                  .build();
          methodSpecs.add(instance);
        });
    return methodSpecs;
  }

  private List<MethodSpec> getSuperMethodSpecs() {
    List<MethodSpec> methodSpecs = new ArrayList<>();
    this.annotatedClazz
        .annotatedElement
        .getEnclosedElements()
        .stream()
        .filter(element -> element instanceof ExecutableElement)
        .map(element -> (ExecutableElement) element)
        .forEach(
            method -> {
              ClassName preferenceRoom = ClassName.get(PreferenceRoom.class);
              MethodSpec.Builder builder = MethodSpec.overriding(method);
              MethodSpec methodSpec =
                  builder
                      .addStatement(
                          "$T.inject($N)",
                          preferenceRoom,
                          method.getParameters().get(0).getSimpleName())
                      .build();
              if (methodSpec.returnType != TypeName.get(Void.TYPE)) {
                throw new VerifyException(
                    String.format(
                        "Returned '%s'. only return type can be void.",
                        methodSpec.returnType.toString()));
              }
              methodSpecs.add(methodSpec);
            });
    return methodSpecs;
  }

  private MethodSpec getEntityNameListSpec() {
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("get" + ENTITY_NAME_LIST)
            .addModifiers(PUBLIC)
            .returns(List.class)
            .addStatement("List<String> $N = new $T<>()", ENTITY_NAME_LIST, ArrayList.class);

    this.annotatedClazz.keyNames.forEach(
        entityName -> builder.addStatement("$N.add($S)", ENTITY_NAME_LIST, entityName));

    builder.addStatement("return $N", ENTITY_NAME_LIST);
    return builder.build();
  }

  private ClassName getClassType() {
    return ClassName.get(annotatedClazz.packageName, getClazzName());
  }

  private String getClazzName() {
    return CLAZZ_PREFIX + annotatedClazz.clazzName;
  }

  private ClassName getEntityClassType(PreferenceEntityAnnotatedClass annotatedClass) {
    return ClassName.get(annotatedClass.packageName, getEntityClazzName(annotatedClass));
  }

  private String getEntityClazzName(PreferenceEntityAnnotatedClass annotatedClass) {
    return ENTITY_PREFIX + annotatedClass.entityName;
  }

  private String getEntityInstanceFieldName(String keyName) {
    return FIELD_INSTANCE + StringUtils.toUpperCamel(keyName);
  }

  private TypeName getContextPackageType() {
    return TypeName.get(annotatedElementUtils.getTypeElement(PACKAGE_CONTEXT).asType());
  }
}
