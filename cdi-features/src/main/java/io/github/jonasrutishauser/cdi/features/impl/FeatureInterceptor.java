package io.github.jonasrutishauser.cdi.features.impl;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Priority(PLATFORM_BEFORE - 900)
@Interceptor
@FeatureSelector
class FeatureInterceptor {

    private final Bean<?> targetBean;
    private FeatureInvoker<?> invoker;

    @Inject
    FeatureInterceptor(@Intercepted Bean<?> targetBean) {
        this.targetBean = targetBean;
    }

    Bean<?> getTargetBean() {
        return targetBean;
    }

    <T> void setInvoker(FeatureInvoker<?> invoker) {
        this.invoker = invoker;
    }

    @AroundInvoke
    Object intercept(InvocationContext context) throws Throwable {
        return invoker.invoke(context.getMethod(), context.getParameters());
    }

}
