package io.github.jonasrutishauser.cdi.features.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class SampleFeature2Selector implements ContextualSelector<SampleFeature2> {
    private final Config config;

    @Inject
    SampleFeature2Selector(Config config) {
        this.config = config;
    }

    @Override
    public boolean selected(Context<? extends SampleFeature2> context) {
        assertEquals("SampleFeature2", context.instance().test());
        return config.getSelected() == 2;
    }

    @PreDestroy
    void destroy() {
        config.setSelectorDestroyed();
    }
}