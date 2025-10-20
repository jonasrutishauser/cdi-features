package io.github.jonasrutishauser.cdi.features.impl;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

public abstract class Accessor {

    @Inject
    BeanManager beanManager;

    @AfterEach
    void featureInterceptor_has_no_current() {
        assertNull(FeatureInterceptor.CURRENT.get());
        FeatureInterceptor.CURRENT.remove();
    }

    protected void clearCache() {
        destroy(beanManager.createInstance().select(Cache.class));
    }

    private <T> void destroy(Instance<T> instance) {
        instance.destroy(instance.get());
    }

}
