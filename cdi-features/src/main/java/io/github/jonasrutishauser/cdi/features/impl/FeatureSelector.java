package io.github.jonasrutishauser.cdi.features.impl;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

@Target(TYPE)
@Retention(RUNTIME)
@InterceptorBinding
@interface FeatureSelector {
    public final static class Literal extends AnnotationLiteral<FeatureSelector> implements FeatureSelector {
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;
    }
}
