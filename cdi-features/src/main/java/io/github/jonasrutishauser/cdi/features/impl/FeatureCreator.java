package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.feature;
import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.isDefined;
import static io.github.jonasrutishauser.cdi.features.impl.FeaturesExtension.createInstance;
import static io.github.jonasrutishauser.cdi.features.impl.FeaturesExtension.hasDefinedSelector;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Instance.Handle;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;

public class FeatureCreator implements SyntheticBeanCreator<Object>  {

    static final String IDENTIFIER = "identfier";
    static final String BEANS_IDENTIFIER = "beans.identfier";

    @FunctionalInterface
    interface Lookup {
        <T> T getReference(Class<T> type) throws IllegalArgumentException;
    }

    static <T> T create(Bean<T> ownBean, Stream<? extends Handle<? extends T>> instancesStream, Lookup lookup) {
        Map<Bean<? extends T>, T> instances = instancesStream.collect(Collectors.toMap(Handle::getBean, Handle::get));
        Map<Bean<? extends T>, ContextualSelector> selectors = getSelectors(lookup, instances);
        FeatureContext context = (FeatureContext) lookup.getReference(BeanManager.class)
                .getContext(FeatureScoped.class);
        context.setInstances(ownBean, new FeatureInstances<>(instances, selectors, lookup.getReference(Cache.class)));
        return instances.values().iterator().next();
    }

    private static <T> Map<Bean<? extends T>, ContextualSelector> getSelectors(Lookup lookup,
            Map<Bean<? extends T>, T> instances) {
        Map<Bean<? extends T>, ContextualSelector> selectors = new HashMap<>();
        for (Entry<Bean<? extends T>, T> instance : instances.entrySet()) {
            Feature feature = feature(instance.getKey()).orElseThrow();
            ContextualSelector selector;
            if (feature.remaining()) {
                selector = null;
            } else if (hasDefinedSelector(feature)) {
                try {
                    selector = lookup.getReference(feature.selector());
                } catch (IllegalArgumentException e) {
                    selector = createInstance(feature.selector());
                }
            } else if (isDefined(feature.propertyKey())) {
                selector = lookup.getReference(ConfigurationSelector.class);
            } else {
                selector = (Selector) instance.getValue();
            }
            selectors.put(instance.getKey(), selector);
        }
        return selectors;
    }

    @Override
    public Object create(Instance<Object> lookup, Parameters params) {
        BeanContainer beanContainer = lookup.select(BeanContainer.class).get();
        @SuppressWarnings("unchecked")
        Bean<Object> ownBean = (Bean<Object>) beanContainer
                .resolve(beanContainer.getBeans(Object.class, params.get(IDENTIFIER, Annotation.class)));
        return create(ownBean, //
                Arrays.stream(params.get(BEANS_IDENTIFIER, Annotation[].class)) //
                        .map(lookup::select) //
                        .map(Instance::getHandle), //
                new Lookup() {
                    @Override
                    public <T> T getReference(Class<T> type) throws IllegalArgumentException {
                        Instance<T> instance = lookup.select(type);
                        if (!instance.isResolvable()) {
                            throw new IllegalArgumentException("No bean found for required type [" + type + "]");
                        }
                        return instance.get();
                    }
                });
    }
}
