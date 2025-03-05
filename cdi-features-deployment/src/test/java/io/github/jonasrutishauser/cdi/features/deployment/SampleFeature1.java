package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.github.jonasrutishauser.cdi.features.Feature.Cache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@Feature(cache = @Cache(durationMillisProperty = "cache.${type}.millis"))
class SampleFeature1 implements SampleFeature, Selector {
    private final Config config;

    @Inject
    SampleFeature1(Config config) {
        this.config = config;
    }

    @Override
    public String test() {
        return "SampleFeature1";
    }

    @Override
    public boolean selected() {
        return config.getSelected() == 1;
    }

    @PreDestroy
    void destroy() {
        config.setDestroyed();
    }
}