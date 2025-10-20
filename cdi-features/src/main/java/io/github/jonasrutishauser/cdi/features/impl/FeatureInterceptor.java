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

    static final ThreadLocal<FeatureInterceptor> CURRENT = new ThreadLocal<>();

    private final Bean<?> targetBean;
    private FeatureInvoker<?> invoker;

    @Inject
    FeatureInterceptor(@Intercepted Bean<?> targetBean) {
        this.targetBean = targetBean;
        CURRENT.set(this);
    }

    Bean<?> getTargetBean() {
        return targetBean;
    }

    void setInvoker(FeatureInvoker<?> invoker) {
        this.invoker = invoker;
        CURRENT.remove();
    }

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return invoker.invoke(context);
    }

}
