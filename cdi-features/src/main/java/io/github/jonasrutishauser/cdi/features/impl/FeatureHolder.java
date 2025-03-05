package io.github.jonasrutishauser.cdi.features.impl;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

class FeatureHolder<T> {

    private final Contextual<T> contextual;
    private final CreationalContext<T> creationalContext;

    private T instance;
    private FeatureInstances<T> instances;

    public FeatureHolder(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        this.creationalContext = creationalContext;
        this.contextual = contextual;
    }

    public T selected() {
        if (instance == null) {
            init();
        }
        return instances.selected(contextual);
    }

    public void setInstances(FeatureInstances<T> features) {
        this.instances = features;
    }

    private synchronized void init() {
        if (instance == null) {
            instance = contextual.create(creationalContext);
            if (instances == null) {
                throw new IllegalStateException("featues should have been set from the callback");
            }
        }
    }

    public void destroy() {
        if (instance != null) {
            contextual.destroy(instance, creationalContext);
            instance = null;
            instances = null;
        }
    }

}
