package io.github.jonasrutishauser.cdi.features;

import jakarta.enterprise.inject.spi.Bean;

public interface ContextualSelector {
    boolean selected(Context context);

    interface Context {
        Bean<?> bean();

        Object instance();
    }
}
