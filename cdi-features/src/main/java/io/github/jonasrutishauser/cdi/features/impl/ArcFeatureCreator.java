package io.github.jonasrutishauser.cdi.features.impl;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.quarkus.arc.SyntheticCreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;

public class ArcFeatureCreator {

    public static <T> T create(SyntheticCreationalContext<T> context, TypeLiteral<Instance<T>> instanceType,
            Bean<T> ownBean, Feature featureLiteral) {
        Instance<T> instance = context.getInjectedReference(instanceType, featureLiteral);
        return FeatureCreator.create(ownBean, instance.handlesStream(), context::getInjectedReference);
    }

}
