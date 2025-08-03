package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.impl.FeaturesExtension.feature;
import static io.github.jonasrutishauser.cdi.features.impl.FeaturesExtension.isDefined;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.OptionalLong;

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.ContextualSelector.Context;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;

class FeatureInstances<T> {
    private final Map<Bean<? extends T>, T> instances;
    private final Map<Bean<? extends T>, ContextualSelector> selectors;
    private final Cache cache;

    public FeatureInstances(Map<Bean<? extends T>, T> instances, Map<Bean<? extends T>, ContextualSelector> selectors,
            Cache cache) {
        this.instances = instances;
        this.selectors = selectors;
        this.cache = cache;
    }

    public T selected(Contextual<T> contextual) {
        return instances.get(cache.compute(contextual, selectors.keySet(), this::isSelected,
                bean -> cacheDurationInMillis(contextual, bean)));
    }

    private Boolean isSelected(Bean<?> bean) {
        ContextualSelector selector = selectors.get(bean);
        try {
            if (selector == null) {
                return null;
            }
            if (selector instanceof Selector s) {
                return s.selected();
            }
            return selector.selected(new Context() {
                @Override
                public Bean<?> bean() {
                    return bean;
                }

                @Override
                public Object instance() {
                    return instances.get(bean);
                }
            });
        } catch (RuntimeException e) {
            return false;
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
                    type = ((BeanAttributes<?>) contextual).getTypes().stream().filter(t -> !Object.class.equals(t))
                            .findAny().map(Type::getTypeName).orElse(type);
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
