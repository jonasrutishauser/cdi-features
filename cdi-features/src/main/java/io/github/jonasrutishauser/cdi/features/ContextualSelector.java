package io.github.jonasrutishauser.cdi.features;

import jakarta.enterprise.inject.spi.Bean;

public interface ContextualSelector<T> {
    boolean selected(Context<? extends T> context);

    interface Context<T> {
        Bean<? extends T> bean();

        T instance();
    }
}
