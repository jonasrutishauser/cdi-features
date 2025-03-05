package io.github.jonasrutishauser.cdi.features.impl;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.jboss.weld.lite.extension.translator.BuildServicesImpl;
import org.jboss.weld.lite.extension.translator.LiteExtensionTranslator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;

class ExtensionIT {

    @BeforeAll
    static void setWeldBuildServices() {
        BuildServicesResolver.setBuildServices(new BuildServicesImpl());
    }

    static Stream<Arguments> extensions() {
        return Stream.of( //
                arguments(named("Full", new FeaturesExtension()), DefinitionException.class), //
                arguments(
                        named("Light", new LiteExtensionTranslator(List.of(TestFeaturesBuildCompatibleExtension.class),
                                ExtensionIT.class.getClassLoader())),
                        DeploymentException.class) //
        );
    }

    static Stream<Arguments> validation() {
        return validationRules().flatMap(
                args -> extensions().map(ex -> arguments(ex.get()[0], ex.get()[1], args.get()[0], args.get()[1])));
    }

    private static Stream<Arguments> validationRules() {
        return Stream.of( //
                arguments(named("selector and remaining is set", SelectorAndRemaining.class),
                        "selector must not be set if remaining is true"), //
                arguments(named("propertyKey and remaining is set", PropertyKeyAndRemaining.class),
                        "propertyKey must not be set if remaining is true"), //
                arguments(named("selector and propertyKey is set", SelectorAndPropertyKey.class),
                        "propertyKey must not be set if selector is set"), //
                arguments(named("not implementing selector", NotImplementingSelector.class),
                        " must implement io.github.jonasrutishauser.cdi.features.Selector if no other selector is defined") //
        );
    }

    @ParameterizedTest(name = "{2} - {0}")
    @MethodSource("validation")
    void validate(Extension extension, Class<? extends RuntimeException> exceptionType, Class<?> beanClass,
            String expectedMessage) {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance().disableDiscovery()
                .addExtensions(extension).addBeanClasses(beanClass);
        try {
            initializer.addExtensions(new ConfigExtension());
        } catch (NoClassDefFoundError e) {
            // ignore
        }

        RuntimeException exception = assertThrows(exceptionType, initializer::initialize);

        assertThat(exception).hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> cachePropertyvalidation() {
        return Stream.of( //
                arguments(named("on class", CacheWithPropertyOnClass.class)), //
                arguments(named("on fieald", CacheWithPropertyOnField.class)), //
                arguments(named("on method", CacheWithPropertyOnMethod.class)) //
        ).flatMap(args -> extensions().map(ex -> arguments(ex.get()[0], ex.get()[1], args.get()[0])));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachePropertyvalidation")
    void validateCacheProperty(Extension extension, Class<? extends RuntimeException> exceptionType,
            Class<?> beanClass) {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance().disableDiscovery()
                .addExtensions(extension).addBeanClasses(beanClass);
        boolean shouldFail = false;
        try {
            initializer.addExtensions(new ConfigExtension());
        } catch (NoClassDefFoundError e) {
            shouldFail = true;
        }

        if (shouldFail && extension instanceof FeaturesExtension) {
            RuntimeException exception = assertThrows(exceptionType, initializer::initialize);

            assertThat(exception).hasMessageContaining("cache durationMillisProperty must not be set");
        } else {
            try (SeContainer container = initializer.initialize()) {
                Bean<CacheWithPropertyOnClass> bean = container.select(CacheWithPropertyOnClass.class,
                        CacheWithPropertyOnClass.class.getAnnotation(Feature.class)).getHandle().getBean();
                String expectedProperty = shouldFail ? "" : "some.property";
                bean.getQualifiers().forEach(qualifier -> {
                    if (qualifier instanceof Feature f) {
                        assertEquals(expectedProperty, f.cache().durationMillisProperty());
                    }
                });
            }
        }
    }

    static Stream<Arguments> propertyKeyvalidation() {
        return Stream.of( //
                arguments(named("on class", WithPropertyKeyOnClass.class)), //
                arguments(named("on fieald", WithPropertyKeyOnField.class)), //
                arguments(named("on method", WithPropertyKeyOnMethod.class)) //
        ).flatMap(args -> extensions().map(ex -> arguments(ex.get()[0], ex.get()[1], args.get()[0])));
    }

    @ParameterizedTest(name = "{2} - {0}")
    @MethodSource("propertyKeyvalidation")
    void validateProperty(Extension extension, Class<? extends RuntimeException> exceptionType, Class<?> beanClass) {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance().disableDiscovery()
                .addExtensions(extension).addBeanClasses(beanClass);
        boolean shouldFail = false;
        try {
            initializer.addExtensions(new ConfigExtension());
        } catch (NoClassDefFoundError e) {
            shouldFail = true;
        }

        if (shouldFail) {
            RuntimeException exception = assertThrows(exceptionType, initializer::initialize);
    
            assertThat(exception).hasMessageContaining("propertyKey must not be set");
        } else {
            initializer.initialize().close();
        }
    }

    @Feature(selector = SelectorAndRemaining.class, remaining = true)
    static class SelectorAndRemaining implements Selector {
        @Override
        public boolean selected() {
            return false;
        }
    }

    @Feature(propertyKey = "some.property", remaining = true)
    static class PropertyKeyAndRemaining {}

    @Feature(selector = SelectorAndPropertyKey.class, propertyKey = "some.property")
    static class SelectorAndPropertyKey implements Selector {
        @Override
        public boolean selected() {
            return false;
        }
    }

    @Feature(propertyValue = "some-value")
    static class NotImplementingSelector {}

    @Feature(remaining = true, cache = @Feature.Cache(durationMillisProperty = "some.property"))
    static class CacheWithPropertyOnClass {}

    static class CacheWithPropertyOnField {
        @Produces
        @Feature(remaining = true, cache = @Feature.Cache(durationMillisProperty = "some.property"))
        final CacheWithPropertyOnClass field = new CacheWithPropertyOnClass();
    }

    static class CacheWithPropertyOnMethod {
        @Produces
        @Feature(remaining = true, cache = @Feature.Cache(durationMillisProperty = "some.property"))
        CacheWithPropertyOnClass method() {
            return new CacheWithPropertyOnClass();
        }
    }

    @Feature(propertyKey = "some.property")
    static class WithPropertyKeyOnClass {}

    static class WithPropertyKeyOnField {
        @Produces
        @Feature(propertyKey = "some.property")
        final WithPropertyKeyOnClass field = new WithPropertyKeyOnClass();
    }

    static class WithPropertyKeyOnMethod {
        @Produces
        @Feature(propertyKey = "some.property")
        WithPropertyKeyOnClass method() {
            return new WithPropertyKeyOnClass();
        }
    }

    public static class TestFeaturesBuildCompatibleExtension extends FeaturesBuildCompatibleExtension {
        @Override
        @Discovery
        public void registerScope(MetaAnnotations metaAnnotations) {
            super.registerScope(metaAnnotations);
        }

        @Override
        @Discovery
        public void registerTypes(ScannedClasses scannedClasses) {
            super.registerTypes(scannedClasses);
        }

        @Override
        @Enhancement(types = Cache.class)
        public void setScopeOfCache(ClassConfig classConfig) {
            super.setScopeOfCache(classConfig);
        }

        @Override
        @Priority(LIBRARY_AFTER + 900)
        @Enhancement(types = Object.class, withSubtypes = true, withAnnotations = Feature.class)
        public void addIdentifier(ClassConfig classConfig, Messages messages) {
            super.addIdentifier(classConfig, messages);
        }

        @Override
        @Registration(types = Object.class)
        public void discoverFeatures(BeanInfo bean, Types types, Messages messages) {
            super.discoverFeatures(bean, types, messages);
        }

        @Override
        @Priority(LIBRARY_AFTER + 500)
        @Synthesis
        public void registerFeatureSelectorBeans(SyntheticComponents components, Types types) {
            super.registerFeatureSelectorBeans(components, types);
        }
    }

}
