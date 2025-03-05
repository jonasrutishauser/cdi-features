package io.github.jonasrutishauser.cdi.features.impl;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

public class FeatureContext implements AlterableContext {

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Map<Contextual<?>, FeatureHolder<?>> holders = new ConcurrentHashMap<>();

    @Override
    public Class<? extends Annotation> getScope() {
        return FeatureScoped.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        @SuppressWarnings("unchecked")
        FeatureHolder<T> holder = (FeatureHolder<T>) holders.computeIfAbsent(contextual,
                c -> new FeatureHolder<>(contextual, creationalContext));
        return holder.selected();
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        FeatureHolder<T> holder = getHolder(contextual);
        return holder == null ? null : holder.selected();
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        FeatureHolder<?> removed = holders.remove(contextual);
        if (removed != null) {
            removed.destroy();
        }
    }

    <T> void setInstances(Bean<T> bean, FeatureInstances<T> features) {
        FeatureHolder<T> holder = getHolder(bean);
        holder.setInstances(features);
    }

    @SuppressWarnings("unchecked")
    private <T> FeatureHolder<T> getHolder(Contextual<T> bean) {
        return (FeatureHolder<T>) this.holders.get(bean);
    }

    void stop() {
        if (active.compareAndSet(true, false)) {
            holders.values().forEach(FeatureHolder::destroy);
            holders.clear();
        }
    }

}
