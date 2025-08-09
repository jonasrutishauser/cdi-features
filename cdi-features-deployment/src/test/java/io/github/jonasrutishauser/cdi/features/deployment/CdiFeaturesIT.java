package io.github.jonasrutishauser.cdi.features.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;

import io.github.jonasrutishauser.cdi.features.NoSelectedFeatureException;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

class CdiFeaturesIT {

    @RegisterExtension
    static final QuarkusUnitTest quarkusConfig = new QuarkusUnitTest() //
            .setFlatClassPath(true) // needed for invoker
            .withApplicationRoot(archive -> archive //
                    .addClasses(Config.class, NotAFeature.class, GenericSampleFeature.class, SampleFeature.class,
                            SampleFeature1.class, SampleFeature2.class, SampleFeature2Selector.class,
                            SampleFeature3.class, SampleFeature4.class, SampleFeatureNever.class, NeverSelector.class,
                            SampleFeatureRemaining.class, NeverFeature.class, AlwaysFeature.class,
                            DefaultNotAFeature.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            ).overrideConfigKey("quarkus.log.category.\"io.quarkus.arc\".level", "DEBUG");

    @Inject
    Config config;

    @Inject
    SampleFeature sampleFeature;

    @Inject
    NotAFeature notAFeature;

    @Inject
    BeanManager beanManager;

    @Inject
    Instance<GenericSampleFeature<?>> instance;

    private void setSelected(int selected) {
        try {
            destroy(beanManager.createInstance().select(Class.forName("io.github.jonasrutishauser.cdi.features.impl.Cache"))); // clear cache
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        config.setSelected(selected);
    }

    private <T> void destroy(Instance<T> instance) {
        instance.destroy(instance.get());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "cache.io.github.jonasrutishauser.cdi.features.AbstractIT$SampleFeature.millis", value="1")
    @SetSystemProperty(key = "feature", value = "31")
    void sampleFeature1() throws InterruptedException {
        setSelected(1);

        assertEquals("SampleFeature1", sampleFeature.test());
        assertEquals("SampleFeature1", sampleFeature.test());
        Thread.sleep(1); // wait for cache to expire
        assertEquals("SampleFeature1", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "32")
    void sampleFeature2() {
        setSelected(2);

        assertEquals("SampleFeature2", sampleFeature.test());
        assertEquals("SampleFeature2", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "3")
    void sampleFeature3() {
        setSelected(42);

        assertEquals("SampleFeature3", sampleFeature.test());
        assertEquals("SampleFeature3", sampleFeature.test());
    }

    @Test
    @SetSystemProperty(key = "feature", value = "some value")
    @SetSystemProperty(key = "feature4", value = "some value")
    void sampleFeature4() {
        setSelected(13);

        assertEquals("SampleFeature4", sampleFeature.test());
        assertEquals("SampleFeature4", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "42")
    void sampleFeatureRemaining() {
        setSelected(42);
        do {
            config.setSelected(new Random().nextInt());
        } while (config.getSelected() == 1 || config.getSelected() == 2);

        assertEquals("SampleFeatureRemaining", sampleFeature.test());
        assertEquals("SampleFeatureRemaining", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "33")
    void genericSampleFeature2() {
        setSelected(2);
        @SuppressWarnings("serial")
        Instance<GenericSampleFeature<String>> genericFeatureInstance = instance.select(new TypeLiteral<GenericSampleFeature<String>>() {});

        assertEquals("SampleFeature2", genericFeatureInstance.get().test());
        assertEquals("SampleFeature2", genericFeatureInstance.get().test());
        genericFeatureInstance.destroy(genericFeatureInstance.get());
        assertTrue(config.isDestroyed(), "Beans should be destroyed");
        assertFalse(config.isFeature3Created(), "Feature3 should not be created");
        assertEquals("SampleFeature2", genericFeatureInstance.get().test());
    }

    @Test
    void noSelectedFeature() {
        @SuppressWarnings("serial")
        GenericSampleFeature<StringBuilder> genericFeatureInstance = instance
                .select(new TypeLiteral<GenericSampleFeature<StringBuilder>>() {}).get();

        NoSelectedFeatureException exception = assertThrows(NoSelectedFeatureException.class,
                genericFeatureInstance::test);

        assertEquals(
                "No selected feature for io.github.jonasrutishauser.cdi.features.deployment.GenericSampleFeature<java.lang.StringBuilder>",
                exception.getMessage());
        assertTrue(exception.getContextual() instanceof Bean);
    }

    @RepeatedTest(3)
    void always() {
        AlwaysFeature.counter.set(0); // reset counter
        @SuppressWarnings("serial")
        StringBuffer result = instance.select(new TypeLiteral<GenericSampleFeature<StringBuffer>>() {}).get().test();

        assertEquals("always 0", result.toString());
    }

    @RepeatedTest(3)
    void concurrency() {
        AlwaysFeature.counter.set(0); // reset counter
        @SuppressWarnings("serial")
        GenericSampleFeature<StringBuffer> feature = instance.select(new TypeLiteral<GenericSampleFeature<StringBuffer>>() {}).get();

        CountDownLatch latch = new CountDownLatch(1);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    latch.await();
                    assertEquals("always 0", feature.test().toString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        latch.countDown(); // release all threads

        assertEquals("always 0", feature.test().toString());
    }

    @Test
    void defaultScoped() {
        CharSequence result = notAFeature.test();

        assertEquals("default", result);
    }

}
