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
import jakarta.interceptor.InvocationContext;

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

    public Object invoke(InvocationContext context) throws Exception {
        Bean<?> selectedBean = selectedBean(targetBean);
        return invoke(context, selectedBean, instances.get(selectedBean).get());
    }

    /**
     * @param selectedBean used in overriding class
     */
    protected Object invoke(InvocationContext context, Bean<?> selectedBean, T target) throws Exception {
        Method resolvedMethod = map(context.getMethod());
        resolvedMethod.setAccessible(true);
        try {
            return resolvedMethod.invoke(target, context.getParameters());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
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
