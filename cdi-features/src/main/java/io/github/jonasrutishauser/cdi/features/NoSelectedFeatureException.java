package io.github.jonasrutishauser.cdi.features;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.BeanAttributes;

public class NoSelectedFeatureException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient Contextual<?> contextual;

    public NoSelectedFeatureException(Contextual<?> contextual) {
        super(getMessage(contextual));
        this.contextual = contextual;
    }

    private static String getMessage(Contextual<?> contextual) {
        Object description = contextual instanceof BeanAttributes ? getType((BeanAttributes<?>) contextual)
                : contextual;
        return "No selected feature for " + description;
    }

    public static Type getType(BeanAttributes<?> contextual) {
        return contextual.getTypes().stream().reduce(Object.class,
                (a, b) -> Object.class.equals(a) || b instanceof ParameterizedType ? b : a);
    }

    public Contextual<?> getContextual() {
        return contextual;
    }

}
