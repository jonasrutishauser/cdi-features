package io.github.jonasrutishauser.cdi.features.impl;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.invoke.Invoker;

public class FeatureInvokerInvoker<T> extends FeatureInvoker<T> {

    private Map<Bean<?>, Map<Method, ? extends Invoker<?, ?>>> invokers;

    public FeatureInvokerInvoker(Bean<T> targetBean, Map<Bean<? extends T>, Supplier<T>> instances,
            Map<Bean<? extends T>, ContextualSelector<? super T>> selectors, Cache cache,
            Map<Bean<?>, Map<Method, ? extends Invoker<?, ?>>> invokers) {
        super(targetBean, instances, selectors, cache);
        this.invokers = invokers;
    }

    @Override
    protected Object invoke(Method method, Object[] parameters, Bean<?> selectedBean) throws Throwable {
        @SuppressWarnings("unchecked")
        Map<Method, Invoker<T, ?>> selectedInvokers = (Map<Method, Invoker<T, ?>>) invokers.get(selectedBean);
        if (selectedInvokers != null) {
            Invoker<T, ?> invoker = selectedInvokers.get(method);
            if (invoker == null) {
                invoker = selectedInvokers.entrySet().stream() //
                        .filter(entry -> entry.getKey().getName().equals(method.getName()) //
                                && Arrays.equals(entry.getKey().getParameterTypes(), method.getParameterTypes())) //
                        .map(Map.Entry::getValue) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchMethodException("No invoker found for method: " + method));
            }
            return invoker.invoke(instances.get(selectedBean).get(), parameters);
        }
        return super.invoke(method, parameters, selectedBean);
    }

}
