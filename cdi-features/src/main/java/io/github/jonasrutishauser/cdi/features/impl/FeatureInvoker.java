package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.NoSelectedFeatureException.getType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import jakarta.enterprise.inject.spi.Bean;

class FeatureInvoker<T> extends FeatureInstances<T> {
    protected final Bean<T> targetBean;
    private final Class<?> type;

    public FeatureInvoker(Bean<T> targetBean, Map<Bean<? extends T>, Supplier<T>> instances,
            Map<Bean<? extends T>, ContextualSelector<? super T>> selectors, Cache cache) {
        super(instances, selectors, cache);
        this.targetBean = targetBean;
        Type targetType = getType(targetBean);
        this.type = (Class<?>) (targetType instanceof Class ? targetType : ((ParameterizedType) targetType).getRawType());
    }

    public Object invoke(Method method, Object[] parameters) throws Throwable {
        Bean<?> selectedBean = selectedBean(targetBean);
        return invoke(method, parameters, selectedBean);
    }

    protected Object invoke(Method method, Object[] parameters, Bean<?> selectedBean) throws Throwable {
        Method resolvedMethod = map(method);
        resolvedMethod.setAccessible(true);
        try {
            return resolvedMethod.invoke(instances.get(selectedBean).get(), parameters);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Method map(Method method) {
        if (method.getDeclaringClass().isAssignableFrom(type)) {
            return method;
        }
        try {
            return getDeclaredMethod(method, type);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No method found: " + method);
        }
    }

    private Method getDeclaredMethod(Method method, Class<?> clazz) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            // continue searching in superclasses
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return getDeclaredMethod(method, iface);
            } catch (NoSuchMethodException e) {
                // continue searching in interfaces
            }
        }
        if (clazz.getSuperclass() != null) {
            return getDeclaredMethod(method, clazz.getSuperclass());
        }
        throw new NoSuchMethodException("No method found");
    }
}
