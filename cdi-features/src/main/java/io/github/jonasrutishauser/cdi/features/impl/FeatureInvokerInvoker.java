package io.github.jonasrutishauser.cdi.features.impl;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.invoke.Invoker;
import jakarta.interceptor.InvocationContext;

class FeatureInvokerInvoker<T> extends FeatureInvoker<T> {

    private Map<Bean<?>, Map<Method, ? extends Invoker<?, ?>>> invokers;

    public FeatureInvokerInvoker(Bean<T> targetBean, Map<Bean<? extends T>, Supplier<T>> instances,
            Map<Bean<? extends T>, ContextualSelector<? super T>> selectors, Cache cache,
            Map<Bean<?>, Map<Method, ? extends Invoker<?, ?>>> invokers) {
        super(targetBean, instances, selectors, cache);
        this.invokers = invokers;
    }

    @Override
    protected Object invoke(InvocationContext context, Bean<?> selectedBean, T target) throws Exception {
        @SuppressWarnings("unchecked")
        Map<Method, Invoker<T, ?>> selectedInvokers = (Map<Method, Invoker<T, ?>>) invokers.get(selectedBean);
        if (selectedInvokers != null) {
            Method method = context.getMethod();
            Invoker<T, ?> invoker = selectedInvokers.get(method);
            if (invoker == null) {
                invoker = selectedInvokers.entrySet().stream() //
                        .filter(entry -> entry.getKey().getName().equals(method.getName())
                                && Arrays.equals(entry.getKey().getParameterTypes(), method.getParameterTypes())
                                && entry.getKey().getReturnType().equals(method.getReturnType())) //
                        .map(Map.Entry::getValue) //
                        .findFirst() //
                        .orElseThrow(() -> new IllegalStateException("No method found: " + method));
            }
            return invoker.invoke(target, context.getParameters());
        }
        return super.invoke(context, selectedBean, target);
    }

}
