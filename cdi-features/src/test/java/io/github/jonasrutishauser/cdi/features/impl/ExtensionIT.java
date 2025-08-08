package io.github.jonasrutishauser.cdi.features.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jboss.weld.environment.se.WeldSEProvider;
import org.jboss.weld.lite.extension.translator.BuildServicesImpl;
import org.jboss.weld.lite.extension.translator.LiteExtensionTranslator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Selector;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;

class ExtensionIT {

    @BeforeAll
    static void setWeldBuildServices() {
        BuildServicesResolver.setBuildServices(new BuildServicesImpl());
        CDI.setCDIProvider(new WeldSEProvider());
    }

    static Stream<Arguments> extensions() {
        return Stream.of( //
                arguments(named("Full", new FeaturesExtension()), DefinitionException.class), //
                arguments(
                        named("Light", forceAddExtension(new LiteExtensionTranslator(Collections.emptySet(),
                                ExtensionIT.class.getClassLoader()), FeaturesBuildCompatibleExtension.class)),
                        DeploymentException.class) //
        );
    }

    // Workaround as the Weld implementation ignores all build compatible extensions with a SkipIfPortableExtensionPresent annotation.
    private static Extension forceAddExtension(LiteExtensionTranslator translator,
            Class<? extends BuildCompatibleExtension> extensionClass) {
        try {
            Field utilField = LiteExtensionTranslator.class.getDeclaredField("util");
            utilField.setAccessible(true);
            Object util = utilField.get(translator);
            Field classesField = util.getClass().getDeclaredField("extensionClasses");
            classesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Class<?>> extensionClasses = (Map<String, Class<?>>) classesField.get(util);
            Field instancesField = util.getClass().getDeclaredField("extensionClassInstances");
            instancesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Class<?>, Object> extensionClassInstances = (Map<Class<?>, Object>) instancesField.get(util);
            extensionClasses.put(extensionClass.getName(), extensionClass);
            extensionClassInstances.put(extensionClass, extensionClass.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return translator;
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
                        " must implement io.github.jonasrutishauser.cdi.features.Selector if no other selector is defined"), //
                arguments(named("wrong types on ContextualSelector", WithWrongSelector.class),
                        "selector type io.github.jonasrutishauser.cdi.features.impl.ExtensionIT$WrongSelector accepts beans with type java.util.List<java.lang.String>, which is not a type of the bean") //
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
                arguments(named("on field", CacheWithPropertyOnField.class)), //
                arguments(named("on method", CacheWithPropertyOnMethod.class)) //
        ).flatMap(args -> extensions().map(ex -> arguments(ex.get()[0], ex.get()[1], args.get()[0])));
    }

    @ParameterizedTest(name = "{2} - {0}")
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
    static class PropertyKeyAndRemaining implements Supplier<String> {
        @Override
        public String get() {
            return null;
        }
    }

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

        @Feature
        void someOtherMethod() {
            // just to have a method
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

    @Feature(selector = WrongSelector.class)
    static class WithWrongSelector {}

    static class WrongSelector extends WrongSelectorSuper<Object, List<String>> implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    static class WrongSelectorSuper<S, T extends List<? extends CharSequence>> implements WrongSelectorInterface<T> {
        @Override
        public boolean selected(Context<? extends T> context) {
            return false;
        }
    }

    static interface WrongSelectorInterface<S> extends ContextualSelector<S> {}

}
