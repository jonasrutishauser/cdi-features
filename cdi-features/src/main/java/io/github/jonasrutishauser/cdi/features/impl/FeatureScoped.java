package io.github.jonasrutishauser.cdi.features.impl;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

@Retention(RUNTIME)
@Target({})
@NormalScope
public @interface FeatureScoped {}
