package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@Feature(selector = SampleFeature2Selector.class)
class SampleFeature2 implements SampleFeature {
    private final Config config;

    @Inject
    SampleFeature2(Config config) {
        this.config = config;
    }

    @Override
    public String test() {
        return "SampleFeature2";
    }

    @PreDestroy
    void destroy() {
        config.setDestroyed();
    }
}