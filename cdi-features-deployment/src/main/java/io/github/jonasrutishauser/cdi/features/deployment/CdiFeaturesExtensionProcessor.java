package io.github.jonasrutishauser.cdi.features.deployment;

import static io.github.jonasrutishauser.cdi.features.impl.ArcFeatureCreator.CACHE_CLASS;
import static io.github.jonasrutishauser.cdi.features.impl.ArcFeatureCreator.CONFIGURATION_SELECTOR_CLASS;
import static io.github.jonasrutishauser.cdi.features.impl.ArcInterceptedFeatureCreator.FEATURE_INVOKER_CLASS;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.logging.Logger;
import org.objectweb.asm.Opcodes;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.github.jonasrutishauser.cdi.features.impl.ArcFeatureCreator;
import io.github.jonasrutishauser.cdi.features.impl.ArcInterceptedFeatureCreator;
import io.github.jonasrutishauser.cdi.features.impl.FeatureScoped;
import io.github.jonasrutishauser.cdi.features.impl.Identified;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.processor.AnnotationLiteralProcessor;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.SignatureBuilder;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.interceptor.InvocationContext;

public class CdiFeaturesExtensionProcessor {

    private static final String FEATURE = "cdi-features";

    private static final Logger LOG = Logger.getLogger(CdiFeaturesExtensionProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AnnotationsTransformerBuildItem setQuarkusDeployment() {
        return new AnnotationsTransformerBuildItem(
                AnnotationTransformation.forClasses().priority(0).whenClass(DotName.createSimple(CACHE_CLASS))
                        .transform(t -> t.addAll(AnnotationInstance.builder(Default.class).build(),
                                AnnotationInstance.builder(Identified.class).value(0l).build())));
    }

    static class UseInterceptor implements BooleanSupplier {
        CdiFeaturesConfiguration configuration;

        @Override
        public boolean getAsBoolean() {
            return configuration.useInterceptor();
        }
    }

    @BuildStep(onlyIf = UseInterceptor.class)
    void useInvocationContextTargetIfPossible(CombinedIndexBuildItem index,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<BytecodeTransformerBuildItem> bytcodeTransformer, CdiFeaturesConfiguration configuration) {
        ClassInfo invocationContext = index.getComputingIndex()
                .getClassByName("io.quarkus.arc.impl.AbstractInvocationContext");
        FieldInfo targetField = invocationContext == null ? null : invocationContext.field("target");
        if (targetField == null) {
            LOG.warn("Could not find target field in AbstractInvocationContext, intercepted features will use reflection");
        } else {
            GeneratedClassGizmoAdaptor classGizmoAdaptor = new GeneratedClassGizmoAdaptor(generatedClass, false);
            String className = "io.quarkus.arc.impl.InvocationContext_Target_Setter";
            MethodDescriptor setTargetMethod = MethodDescriptor.ofMethod(className, "setTarget", void.class,
                    InvocationContext.class, Object.class);
            try (ClassCreator setterClass = ClassCreator.builder().classOutput(classGizmoAdaptor).className(className)
                    .build()) {
                MethodCreator setter = setterClass.getMethodCreator(setTargetMethod)
                        .setModifiers(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC);
                setter.writeInstanceField(FieldDescriptor.of(targetField), setter.getMethodParam(0),
                        setter.getMethodParam(1));
                setter.returnNull();
            }

            // change invoke method to use the setter and then call proceed()
            ClassTransformer ct = new ClassTransformer(FEATURE_INVOKER_CLASS);
            MethodDescriptor invokeDescriptor = MethodDescriptor.ofMethod(FEATURE_INVOKER_CLASS, "invoke", Object.class,
                    InvocationContext.class, Bean.class, Object.class);
            ct.removeMethod(invokeDescriptor);

            MethodCreator invokeMethod = ct.addMethod(invokeDescriptor).setModifiers(Opcodes.ACC_PROTECTED)
                    .addException(Exception.class);
            // set the target
            invokeMethod.invokeStaticMethod(setTargetMethod, invokeMethod.getMethodParam(0),
                    invokeMethod.getMethodParam(2));
            // call proceed
            invokeMethod.returnValue(invokeMethod.invokeInterfaceMethod(
                    MethodDescriptor.ofMethod(InvocationContext.class, "proceed", Object.class),
                    invokeMethod.getMethodParam(0)));

            bytcodeTransformer.produce(new BytecodeTransformerBuildItem.Builder().setCacheable(true)
                    .setClassToTransform(FEATURE_INVOKER_CLASS)
                    .setVisitorFunction((ignored, visitor) -> ct.applyTo(visitor)).build());
        }
    }

    @BuildStep
    void improveFeatureBeans(BeanDiscoveryFinishedBuildItem discoveryFinished,
            BeanRegistrationPhaseBuildItem beanRegistrationPhase, BuildProducer<SyntheticBeanBuildItem> syntheticBean,
            BuildProducer<GeneratedClassBuildItem> generatedClass, CdiFeaturesConfiguration configuration) {
        AnnotationLiteralProcessor annotationLiteralProcessor = beanRegistrationPhase.getBeanProcessor()
                .getAnnotationLiteralProcessor();
        Type objectType = Type.create(Object.class);
        AnnotationInstance featureAnnotation = AnnotationInstance.builder(Feature.class).build();
        Optional<ClassInfo> featureClass = beanRegistrationPhase.getBeanProcessor().getBeanDeployment().getQualifiers()
                .stream().filter(c -> DotName.createSimple(Feature.class).equals(c.name())).findAny();
        GeneratedClassGizmoAdaptor classGizmoAdaptor = new GeneratedClassGizmoAdaptor(generatedClass, true);
        Map<String, Integer> counter = new HashMap<>();
        discoveryFinished.beanStream().withQualifier(Identified.class).withScope(FeatureScoped.class).forEach(bean -> {
            Type beanType = bean.getTypes().stream().filter(t -> !objectType.equals(t)).findAny().get();
            ParameterizedType instanceType = ParameterizedType.create(Instance.class, beanType);
            String classNamePrefix = beanType.toString();
            if (classNamePrefix.contains("<")) {
                classNamePrefix = classNamePrefix.substring(0, classNamePrefix.indexOf('<'));
            }
            String instanceTypeLiteralName = classNamePrefix + "$$instanceTypeLiteral$$"
                    + counter.compute(classNamePrefix, (key, value) -> value == null ? 0 : ++value);
            ClassCreator.builder().classOutput(classGizmoAdaptor).className(instanceTypeLiteralName)
                    .signature(SignatureBuilder.forClass()
                            .setSuperClass(toGizmo(ParameterizedType.create(TypeLiteral.class, instanceType))))
                    .build().close();
            ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem.configure(bean.getBeanClass()) //
                    .types(bean.getTypes().toArray(Type[]::new)) //
                    .addInjectionPoint(instanceType, featureAnnotation) //
                    .addInjectionPoint(Type.create(DotName.createSimple(CACHE_CLASS), Kind.CLASS));
            Class<?> creatorClass;
            if (configuration.useInterceptor()) {
                creatorClass = ArcInterceptedFeatureCreator.class;
                configurator.injectInterceptionProxy(
                        DotName.createSimple(ArcInterceptedFeatureCreator.BINDING_SOURCE_CLASS));
            } else {
                creatorClass = ArcFeatureCreator.class;
                configurator.scope(FeatureScoped.class).addInjectionPoint(Type.create(BeanManager.class));
            }
            configurator.creator(mc -> {
                MethodDescriptor create = MethodDescriptor.ofMethod(creatorClass, "create", Object.class,
                        SyntheticCreationalContext.class, TypeLiteral.class, Bean.class, Feature.class);
                MethodDescriptor instanceLiteralConstructor = MethodDescriptor.ofConstructor(instanceTypeLiteralName);
                mc.returnValue(mc.invokeStaticMethod(create, //
                        mc.getMethodParam(0), //
                        mc.newInstance(instanceLiteralConstructor), //
                        mc.getThis(), annotationLiteralProcessor.create(mc, featureClass.get(), featureAnnotation)));
            });
            addSelectors(configurator, discoveryFinished, beanType, featureAnnotation);
            syntheticBean.produce(configurator.done());
        });
    }

    private void addSelectors(ExtendedBeanConfigurator configurator, BeanDiscoveryFinishedBuildItem discoveryFinished,
            Type beanType, AnnotationInstance featureAnnotation) {
        Type selectorClass = Type.create(Selector.class);
        boolean configurationSelectorAdded = false;
        for (BeanInfo featureBean : discoveryFinished.beanStream().assignableTo(beanType, featureAnnotation)
                .collect()) {
            AnnotationInstance feature = featureBean.getQualifier(DotName.createSimple(Feature.class)).orElseThrow();
            AnnotationValue selectorValue = feature.value("selector");
            AnnotationValue propertyKeyValue = feature.value("propertyKey");
            if (selectorValue != null && !selectorClass.equals(selectorValue.asClass())) {
                if (!discoveryFinished.beanStream().assignableTo(selectorValue.asClass()).isEmpty()) {
                    configurator.addInjectionPoint(selectorValue.asClass());
                }
            } else
                if (!configurationSelectorAdded && propertyKeyValue != null && !propertyKeyValue.asString().isEmpty()) {
                    configurator.addInjectionPoint(
                            Type.create(DotName.createSimple(CONFIGURATION_SELECTOR_CLASS), Kind.CLASS));
                    configurationSelectorAdded = true;
                }
        }
    }

    private io.quarkus.gizmo.Type.ParameterizedType toGizmo(ParameterizedType parameterizedType) {
        return io.quarkus.gizmo.Type.parameterizedType(io.quarkus.gizmo.Type.classType(parameterizedType.name()),
                parameterizedType.arguments().stream().map(type -> {
                    if (type.kind() == Kind.PARAMETERIZED_TYPE) {
                        return toGizmo(type.asParameterizedType());
                    } else {
                        return io.quarkus.gizmo.Type.classType(type.name());
                    }
                }).toArray(io.quarkus.gizmo.Type[]::new));
    }

}
