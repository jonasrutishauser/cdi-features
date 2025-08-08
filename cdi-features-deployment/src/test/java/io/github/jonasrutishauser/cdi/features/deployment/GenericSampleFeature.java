package io.github.jonasrutishauser.cdi.features.deployment;

interface GenericSampleFeature<T extends CharSequence> extends NotAFeature {
    T test();
}