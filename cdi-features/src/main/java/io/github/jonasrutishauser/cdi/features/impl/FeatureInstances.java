package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.NoSelectedFeatureException.getType;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.ContextualSelector.Context;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.impl.Cache.Selection;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;

class FeatureInstances<T> {
    private final Map<Bean<? extends T>, Supplier<T>> instances;
    private final Map<Bean<? extends T>, ContextualSelector<? super T>> selectors;
    private final Cache cache;

    public FeatureInstances(Map<Bean<? extends T>, Supplier<T>> instances, Map<Bean<? extends T>, ContextualSelector<? super T>> selectors,
            Cache cache) {
        this.instances = instances;
        this.selectors = selectors;
        this.cache = cache;
    }

    public T selected(Contextual<T> contextual) {
        return instances.get(cache.compute(contextual, selectors.keySet(), this::isSelected,
                bean -> cacheDurationInMillis(contextual, bean))).get();
    }

    static Optional<Feature> feature(Bean<?> bean) {
        return bean.getQualifiers() //
                .stream() //
                .filter(Feature.class::isInstance) //
                .map(Feature.class::cast) //
                .findAny();
    }

    static boolean isDefined(String value) {
        return !value.isEmpty();
    }

    private Selection isSelected(Bean<?> bean) {
        ContextualSelector<? super T> selector = selectors.get(bean);
        try {
            if (selector == null) {
                return Selection.REMAINING;
            }
            return Selection.of(selector.selected(new Context<T>() {
                @Override
                @SuppressWarnings("unchecked")
                public Bean<? extends T> bean() {
                    return (Bean<? extends T>) bean;
                }

                @Override
                public T instance() {
                    return instances.get(bean).get();
                }
            }));
        } catch (RuntimeException e) {
            return Selection.NOT_SELECTED;
        }
    }

    private long cacheDurationInMillis(Contextual<?> contextual, Bean<?> bean) {
        Feature feature = feature(bean).orElseThrow();
        long durationMillis = feature.cache().durationMillis();
        String property = feature.cache().durationMillisProperty();
        OptionalLong propertyValue = OptionalLong.empty();
        if (isDefined(property)) {
            if (property.contains("${type}")) {
                String type = "<undefined>";
                if (contextual instanceof BeanAttributes) {
                    type = getType((BeanAttributes<?>) contextual).getTypeName();
                }
                property = property.replace("${type}", type);
            }
            propertyValue = ProperyResolver.lookup(property);
        }
        return propertyValue.orElse(durationMillis);
    }

    private static class ProperyResolver {
        public static OptionalLong lookup(String property) {
            return ConfigProvider.getConfig().getOptionalValue(property, Long.class).map(OptionalLong::of)
                    .orElse(OptionalLong.empty());
        }
    }
}
