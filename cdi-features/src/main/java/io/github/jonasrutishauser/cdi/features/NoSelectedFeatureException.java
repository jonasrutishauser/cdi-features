package io.github.jonasrutishauser.cdi.features;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.BeanAttributes;

public class NoSelectedFeatureException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final Contextual<?> contextual;

    public NoSelectedFeatureException(Contextual<?> contextual) {
        super(getMessage(contextual));
        this.contextual = contextual;
    }

    private static String getMessage(Contextual<?> contextual) {
        Object description = contextual instanceof BeanAttributes ? ((BeanAttributes<?>) contextual).getTypes()
                : contextual;
        return "No selected feature for " + description;
    }

    public Contextual<?> getContextual() {
        return contextual;
    }

}
