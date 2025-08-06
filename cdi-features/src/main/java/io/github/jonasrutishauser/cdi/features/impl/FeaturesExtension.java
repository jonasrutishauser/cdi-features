package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.feature;
import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.isDefined;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.github.jonasrutishauser.cdi.features.ThrowableSelector;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.util.TypeLiteral;

public class FeaturesExtension implements Extension {

    private static final Type GENERIC_CONTEXTUAL_SELECTOR_TYPE = new TypeLiteral<ContextualSelector<?>>() {
        private static final long serialVersionUID = 1L;
    }.getType();

    private final FeatureContext context = new FeatureContext();

    private final Set<Bean<?>> featureBeans = new HashSet<>();

    private boolean mpConfigAvailable;

    void registerScope(@Observes BeforeBeanDiscovery bbd) {
        bbd.addScope(FeatureScoped.class, true, false);
    }

    void registerAnnotatedType(@Observes BeforeBeanDiscovery bbd) {
        bbd.addAnnotatedType(Cache.class, Cache.class.getName()).add(ApplicationScoped.Literal.INSTANCE);
        try {
            Class.forName("org.eclipse.microprofile.config.Config", false, getClass().getClassLoader());
            bbd.addAnnotatedType(ConfigurationSelector.class, ConfigurationSelector.class.getName());
            mpConfigAvailable = true;
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }

    void discoverFeatures(@Observes ProcessBean<?> bean, BeanManager beanManager) {
        Optional<Feature> feature = feature(bean.getBean());
        if (feature.isPresent()) {
            validate(feature.get(), bean.getBean(), beanManager, bean::addDefinitionError);
            featureBeans.add(bean.getBean());
        }
    }

    private void validate(Feature feature, Bean<?> bean, BeanManager beanManager, Consumer<Throwable> definitionError) {
        if (feature.remaining()) {
            if (hasDefinedSelector(feature)) {
                definitionError.accept(
                        new IllegalStateException("selector must not be set if remaining is true on bean " + bean));
            }
            if (isDefined(feature.propertyKey())) {
                definitionError.accept(
                        new IllegalStateException("propertyKey must not be set if remaining is true on bean " + bean));
            }
        } else if (hasDefinedSelector(feature)) {
            if (isDefined(feature.propertyKey())) {
                definitionError.accept(
                        new IllegalStateException("propertyKey must not be set if selector is set on bean " + bean));
            }
            validateSelectorType(feature.selector(), bean, beanManager, definitionError);
        } else if (isDefined(feature.propertyKey()) && !mpConfigAvailable) {
            definitionError.accept(new IllegalStateException(
                    "as MicroProfile Config is not available, propertyKey must not be set on bean " + bean));
        } else if (!isDefined(feature.propertyKey()) && bean.getTypes().stream().map(FeaturesExtension::toClass)
                .noneMatch(Selector.class::isAssignableFrom)) {
                    definitionError.accept(new IllegalStateException(
                            bean + " must implement " + Selector.class.getName() + " if no other selector is defined"));
                }
        if (isDefined(feature.propertyValue()) && !isDefined(feature.propertyKey())) {
            definitionError.accept(new IllegalStateException(
                    "propertyValue must not be set if propertyKey is not set on bean " + bean));
        }
        validateCacheDurationMillisProperty(feature, bean, definitionError);
    }

    private void validateSelectorType(Class<? extends ContextualSelector<?>> selector, Bean<?> bean,
            BeanManager beanManager, Consumer<Throwable> definitionError) {
        beanManager.createAnnotatedType(selector).getTypeClosure()
                .stream()
                .filter(type -> type instanceof ParameterizedType parametrizedType
                        && ContextualSelector.class.equals(parametrizedType.getRawType()))
                .map(ParameterizedType.class::cast) //
                .map(type -> type.getActualTypeArguments()[0]) //
                .filter(type -> !bean.getTypes().contains(type)) //
                .forEach(type -> definitionError.accept(new IllegalStateException("selector type " + selector.getName()
                        + " accepts beans with type " + type.getTypeName() + ", which is not a type of the bean " + bean)));
    }

    private void validateCacheDurationMillisProperty(Feature feature, Bean<?> bean,
            Consumer<Throwable> definitionError) {
        if (!mpConfigAvailable && isDefined(feature.cache().durationMillisProperty())) {
            definitionError.accept(new IllegalStateException(
                    "as MicroProfile Config is not available, cache durationMillisProperty must not be set on bean "
                            + bean));
        }
    }

    private static Class<?> toClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return toClass(parameterizedType.getRawType());
        }
        throw new IllegalStateException("unsupported type " + type);
    }

    void registerFeatureSelectorBeans(@Priority(LIBRARY_AFTER + 500) @Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        for (Entry<Type, Set<Bean<?>>> feature : getFeatures().entrySet()) {
            if (beanManager.getBeans(feature.getKey()).isEmpty()) {
                abd.addBean() //
                        .types(feature.getKey()) //
                        .scope(FeatureScoped.class) //
                        .createWith(ctx -> createDummy(beanManager, feature.getKey(), ctx, feature.getValue()));
            }
        }
    }

    private Map<Type, Set<Bean<?>>> getFeatures() {
        Map<Type, Set<Bean<?>>> features = new HashMap<>();
        for (Bean<?> featureBean : featureBeans) {
            for (Type type : featureBean.getTypes()) {
                if (!Object.class.equals(type) && !ContextualSelector.class.equals(type) && !Selector.class.equals(type)
                        && !ThrowableSelector.class.equals(type)) {
                    features.computeIfAbsent(type, t -> new HashSet<>()).add(featureBean);
                }
            }
        }
        return features;
    }

    private <T> T createDummy(BeanManager beanManager, Type type, CreationalContext<Object> ctx,
            Set<Bean<? extends T>> features) {
        Map<Bean<? extends T>, Supplier<T>> instances = getFeatureInstances(beanManager, type, ctx, features);
        context.setInstances(beanManager.resolve(beanManager.getBeans(type)),
                new FeatureInstances<>(instances, getSelectors(beanManager, ctx, instances), getCache(beanManager, ctx)));
        return instances.values().iterator().next().get();
    }

    private Cache getCache(BeanManager beanManager, CreationalContext<Object> ctx) {
        return (Cache) beanManager.getReference(beanManager.resolve(beanManager.getBeans(Cache.class)), Cache.class,
                ctx);
    }

    private static <T> Map<Bean<? extends T>, Supplier<T>> getFeatureInstances(BeanManager beanManager, Type type,
            CreationalContext<Object> ctx, Set<Bean<? extends T>> features) {
        return features.stream().collect(toMap(identity(), bean -> {
            @SuppressWarnings("unchecked")
            T instance = (T) beanManager.getReference(bean, type, ctx);
            return () -> instance;
        }));
    }

    private static <T> Map<Bean<? extends T>, ContextualSelector<? super T>> getSelectors(BeanManager beanManager, CreationalContext<Object> ctx,
            Map<Bean<? extends T>, Supplier<T>> instances) {
        Map<Bean<? extends T>, ContextualSelector<? super T>> selectors = new HashMap<>();
        for (Entry<Bean<? extends T>, Supplier<T>> instance : instances.entrySet()) {
            Feature feature = feature(instance.getKey()).orElseThrow();
            ContextualSelector<?> selector;
            if (feature.remaining()) {
                selector = null;
            } else if (hasDefinedSelector(feature)) {
                Set<Bean<?>> beans = beanManager.getBeans(feature.selector());
                if (beans.isEmpty()) {
                    selector = createInstance(feature.selector());
                } else {
                    selector = (ContextualSelector<?>) beanManager.getReference(beanManager.resolve(beans),
                            GENERIC_CONTEXTUAL_SELECTOR_TYPE, ctx);
                }
            } else if (isDefined(feature.propertyKey())) {
                selector = (ContextualSelector<?>) beanManager.getReference(
                        beanManager.resolve(beanManager.getBeans(ConfigurationSelector.class)),
                        ContextualSelector.class, ctx);
            } else {
                selector = (Selector) instance.getValue().get();
            }
            @SuppressWarnings("unchecked") // is checked in the validate method
            ContextualSelector<? super T> contextualSelector = (ContextualSelector<? super T>) selector;
            selectors.put(instance.getKey(), contextualSelector);
        }
        return selectors;
    }

    static <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to create selector pojo instance " + clazz, e);
        }
    }

    static boolean hasDefinedSelector(Feature feature) {
        return !Selector.class.equals(feature.selector());
    }

    void registerContext(@Observes AfterBeanDiscovery abd) {
        abd.addContext(context);
        try {
            Class<?> shutdownEvent = Class.forName("jakarta.enterprise.event.Shutdown", false,
                    getClass().getClassLoader());
            abd.addObserverMethod() //
                    .observedType(shutdownEvent) //
                    .priority(LIBRARY_AFTER + 900) //
                    .notifyWith(event -> context.stop());
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }

    void stopContext(@Observes BeforeShutdown bs) {
        context.stop();
    }

}
