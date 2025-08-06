package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.isDefined;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.github.jonasrutishauser.cdi.features.ThrowableSelector;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.Type;

@SkipIfPortableExtensionPresent(FeaturesExtension.class)
public class FeaturesBuildCompatibleExtension implements BuildCompatibleExtension {

    private static final String FEATURE_PROPERTY_KEY_MEMBER = "propertyKey";

    private boolean quarkusDeployment;

    private boolean mpConfigAvailable;

    private long currentIdentifier = Long.MIN_VALUE;

    private final Set<BeanInfo> featureBeans = new HashSet<>();
    private final Set<Type> typesWithDefaultScopedBeans = new HashSet<>();

    @Discovery
    @Priority(LIBRARY_BEFORE + 500)
    public void registerScope(MetaAnnotations metaAnnotations) {
        metaAnnotations.addContext(FeatureScoped.class, true, FeatureContext.class);
    }

    @Discovery
    public void registerTypes(ScannedClasses scannedClasses) {
        scannedClasses.add(Cache.class.getName());
        try {
            Class.forName("org.eclipse.microprofile.config.Config", false, getClass().getClassLoader());
            scannedClasses.add(ConfigurationSelector.class.getName());
            mpConfigAvailable = true;
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }

    @Priority(LIBRARY_BEFORE + 500)
    @Enhancement(types = Cache.class)
    public void setScopeOfCache(ClassConfig classConfig) {
        classConfig.addAnnotation(ApplicationScoped.class);
    }

    @Priority(LIBRARY_AFTER + 900)
    @Enhancement(types = Object.class, withSubtypes = true, withAnnotations = Feature.class)
    public void addIdentifierAndCleanupCacheDurationProperty(ClassConfig classConfig, Messages messages) {
        addIdentifierAndCleanupCacheDurationProperty(classConfig, false, messages);
        for (MethodConfig method : classConfig.methods()) {
            addIdentifierAndCleanupCacheDurationProperty(method, true, messages);
        }
        for (FieldConfig field : classConfig.fields()) {
            addIdentifierAndCleanupCacheDurationProperty(field, true, messages);
        }
    }

    private void addIdentifierAndCleanupCacheDurationProperty(DeclarationConfig declaration, boolean needsProduces,
            Messages messages) {
        AnnotationInfo feature = declaration.info().annotation(Feature.class);
        if (feature != null && (!needsProduces || declaration.info().hasAnnotation(Produces.class))) {
            declaration.addAnnotation(AnnotationBuilder.of(Identified.class).value(currentIdentifier++).build());
            if (!mpConfigAvailable && cacheDurationMillisPropertyIsDefined(feature)) {
                messages.warn("as MicroProfile Config is not available, cache durationMillisProperty will be ignored",
                        declaration.info());
                declaration.removeAnnotation(a -> Feature.class.getName().equals(a.name())) //
                        .addAnnotation(copyFeatureWithoutCacheDurationMillisProperty(feature));
            }
        }
    }

    private boolean cacheDurationMillisPropertyIsDefined(AnnotationInfo feature) {
        return isDefined(feature.member("cache").asNestedAnnotation().member("durationMillisProperty").asString());
    }

    private AnnotationInfo copyFeatureWithoutCacheDurationMillisProperty(AnnotationInfo feature) {
        AnnotationBuilder builder = AnnotationBuilder.of(Feature.class);
        feature.members().forEach((key, value) -> {
            if (key.equals("cache")) {
                builder.member(key, copyCacheWithoutDurationMillisProperty(value.asNestedAnnotation()));
            } else {
                builder.member(key, value);
            }
        });
        return builder.build();
    }

    private AnnotationInfo copyCacheWithoutDurationMillisProperty(AnnotationInfo cache) {
        AnnotationBuilder builder = AnnotationBuilder.of(Feature.Cache.class);
        cache.members().forEach((key, value) -> {
            if (!key.equals("durationMillisProperty")) {
                builder.member(key, value);
            }
        });
        return builder.build();
    }

    @Registration(types = Cache.class)
    public void discoverQuarkusDeployment(BeanInfo bean) {
        if (bean.qualifiers().stream().anyMatch(a -> Identified.class.getName().equals(a.name()))) {
            quarkusDeployment = true;
        }
    }

    @Registration(types = Object.class)
    public void discoverFeatures(BeanInfo bean, Types types, Messages messages) {
        if (bean.qualifiers().stream().anyMatch(a -> Default.class.getName().equals(a.name()))) {
            typesWithDefaultScopedBeans.addAll(bean.types());
        }
        Optional<AnnotationInfo> feature = bean.qualifiers().stream().filter(a -> Feature.class.getName().equals(a.name())).findAny();
        if (feature.isPresent()) {
            validate(feature.get(), bean, types, messages);
            featureBeans.add(bean);
        }
    }

    private void validate(AnnotationInfo feature, BeanInfo bean, Types types, Messages messages) {
        if (feature.member("remaining").asBoolean()) {
            if (hasDefinedSelector(feature, types)) {
                messages.error("selector must not be set if remaining is true", bean);
            }
            if (isDefined(feature.member(FEATURE_PROPERTY_KEY_MEMBER).asString())) {
                messages.error(FEATURE_PROPERTY_KEY_MEMBER + " must not be set if remaining is true", bean);
            }
        } else if (hasDefinedSelector(feature, types)) {
            if (isDefined(feature.member(FEATURE_PROPERTY_KEY_MEMBER).asString())) {
                messages.error(FEATURE_PROPERTY_KEY_MEMBER + " must not be set if selector is set", bean);
            }
            validateSelectorType(feature.member("selector").asType().asClass(), bean, types, messages);
        } else if (isDefined(feature.member(FEATURE_PROPERTY_KEY_MEMBER).asString()) && !mpConfigAvailable) {
            messages.error(
                    "as MicroProfile Config is not available, " + FEATURE_PROPERTY_KEY_MEMBER + " must not be set",
                    bean);
        } else if (!isDefined(feature.member(FEATURE_PROPERTY_KEY_MEMBER).asString())
                && !bean.types().contains(types.of(Selector.class))) {
                    messages.error(
                            "bean must implement " + Selector.class.getName() + " if no other selector is defined",
                            bean);
                }
        if (isDefined(feature.member("propertyValue").asString())
                && !isDefined(feature.member(FEATURE_PROPERTY_KEY_MEMBER).asString())) {
            messages.warn("propertyValue must not be set if " + FEATURE_PROPERTY_KEY_MEMBER + " is not set", bean);
        }
    }

    private void validateSelectorType(ClassType selector, BeanInfo bean, Types types, Messages messages) {
        Type acceptedType = getParametrizedTypeArguments(selector, types.of(ContextualSelector.class), Map.of()).get(0);
        if (!bean.types().contains(acceptedType)) {
            messages.error("selector type " + selector.declaration().name() + " accepts beans with type " + acceptedType
                    + ", which is not a type of the bean", bean);
        }
    }

    private List<Type> getParametrizedTypeArguments(ClassInfo type, Type searchType, Map<String, Type> typeVariables) {
        for (Type iface : type.superInterfaces()) {
            List<Type> arguments = getParametrizedTypeArguments(iface, searchType, typeVariables);
            if (arguments != null) {
                return arguments;
            }
        }
        if (type.superClass() != null) {
            return getParametrizedTypeArguments(type.superClass(), searchType, typeVariables);
        }
        return null;
    }

    private List<Type> getParametrizedTypeArguments(Type type, Type searchType, Map<String, Type> typeVariables) {
        if (type.isParameterizedType()) {
            jakarta.enterprise.lang.model.types.ParameterizedType parameterized = type.asParameterizedType();
            if (searchType.equals(parameterized.genericClass())) {
                return parameterized.typeArguments().stream() //
                        .map(t -> t.isTypeVariable() ? typeVariables.get(t.asTypeVariable().name()) : t) //
                        .toList();
            }
            Map<String, Type> typeVariablesOfInterface = new HashMap<>(typeVariables);
            for (int i = 0; i < parameterized.typeArguments().size(); i++) {
                Type typeArgument = parameterized.typeArguments().get(i);
                typeVariablesOfInterface.put(parameterized.declaration().typeParameters().get(i).name(),
                        typeArgument.isTypeVariable() ? typeVariables.get(typeArgument.asTypeVariable().name())
                                : typeArgument);
            }
            return getParametrizedTypeArguments(parameterized.declaration(), searchType, typeVariablesOfInterface);
        } else {
            return getParametrizedTypeArguments(type.asClass().declaration(), searchType, typeVariables);
        }
    }

    private static boolean hasDefinedSelector(AnnotationInfo feature, Types types) {
        return !types.of(Selector.class).equals(feature.member("selector").asType());
    }

    @Synthesis
    @Priority(LIBRARY_AFTER + 500)
    public void registerFeatureSelectorBeans(SyntheticComponents components, Types types) {
        for (Entry<Type, Set<BeanInfo>> feature : getFeatures(types).entrySet()) {
            AnnotationInfo identifier = AnnotationBuilder.of(Identified.class).value(currentIdentifier++).build();
            @SuppressWarnings("unchecked")
            SyntheticBeanBuilder<Object> beanBuilder = (SyntheticBeanBuilder<Object>) components.addBean(toClass(feature.getKey()));
            beanBuilder.qualifier(quarkusDeployment ? Any.class : Default.class) //
                    .qualifier(identifier) //
                    .type(feature.getKey()) //
                    .scope(FeatureScoped.class) //
                    .createWith(FeatureCreator.class) //
                    .withParam(FeatureCreator.IDENTIFIER, identifier) //
                    .withParam(FeatureCreator.BEANS_IDENTIFIER, getIdentifiers(feature.getValue()));
        }
    }

    private AnnotationInfo[] getIdentifiers(Set<BeanInfo> beans) {
        return beans.stream() //
                .map(BeanInfo::qualifiers) //
                .map(this::getIdentifier) //
                .toArray(AnnotationInfo[]::new);
    }

    private AnnotationInfo getIdentifier(Collection<AnnotationInfo> qualifiers) {
        return qualifiers.stream().filter(a -> Identified.class.getName().equals(a.name())).findAny().orElseThrow();
    }

    private Map<Type, Set<BeanInfo>> getFeatures(Types types) {
        Map<Type, Set<BeanInfo>> features = new HashMap<>();
        Set<Type> excludedTypes = new HashSet<>();
        excludedTypes.add(types.of(Object.class));
        excludedTypes.add(types.of(ContextualSelector.class));
        excludedTypes.add(types.of(Selector.class));
        excludedTypes.add(types.of(ThrowableSelector.class));
        for (BeanInfo featureBean : featureBeans) {
            for (Type type : featureBean.types()) {
                if (!excludedTypes.contains(type) && !typesWithDefaultScopedBeans.contains(type)) {
                    features.computeIfAbsent(type, t -> new HashSet<>()).add(featureBean);
                }
            }
        }
        return features;
    }

    private Class<?> toClass(Type type) {
        try {
            return Class.forName(toClassInfo(type).name(), false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("unsupported type " + type, e);
        }
    }

    private ClassInfo toClassInfo(Type type) {
        if (type.isClass()) {
            return type.asClass().declaration();
        }
        if (type.isParameterizedType()) {
            return type.asParameterizedType().genericClass().declaration();
        }
        throw new IllegalStateException("unsupported type " + type);
    }

    @Synthesis
    @Priority(LIBRARY_BEFORE + 500)
    public void registerScopeStop(SyntheticComponents components, Types types) {
        components.addObserver(Shutdown.class) //
                .priority(LIBRARY_AFTER + 900) //
                .observeWith(ScopeStop.class);
    }

    public static class ScopeStop implements SyntheticObserver<Shutdown> {
        @Override
        public void observe(EventContext<Shutdown> event, Parameters params) throws Exception {
            ((FeatureContext) CDI.current().getBeanManager().getContext(FeatureScoped.class)).stop();
        }
    }

}
