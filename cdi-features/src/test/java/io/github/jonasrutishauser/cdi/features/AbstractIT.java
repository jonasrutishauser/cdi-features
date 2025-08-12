package io.github.jonasrutishauser.cdi.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

import io.github.jonasrutishauser.cdi.features.Feature.Cache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

public abstract class AbstractIT {

    @Inject
    Config config;

    @Inject
    SampleFeature sampleFeature;

    @Inject
    Instance<Object> instance;

    protected void setSelected(int selected) {
        config.setSelected(selected);
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "cache.io.github.jonasrutishauser.cdi.features.AbstractIT$SampleFeature.millis", value="1")
    @SetSystemProperty(key = "feature", value = "31")
    public void sampleFeature1() throws InterruptedException {
        setSelected(1);

        assertEquals("SampleFeature1", sampleFeature.test());
        assertEquals("SampleFeature1", sampleFeature.test());
        Thread.sleep(1); // wait for cache to expire
        assertEquals("SampleFeature1", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "32")
    public void sampleFeature2() {
        setSelected(2);

        assertEquals("SampleFeature2", sampleFeature.test());
        assertEquals("SampleFeature2", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "3")
    public void sampleFeature3() {
        setSelected(42);

        assertEquals("SampleFeature3", sampleFeature.test());
        assertEquals("SampleFeature3", sampleFeature.test());
    }

    @Test
    @SetSystemProperty(key = "feature", value = "some value")
    @SetSystemProperty(key = "feature4", value = "some value")
    public void sampleFeature4() {
        setSelected(13);

        assertEquals("SampleFeature4", sampleFeature.test());
        assertEquals("SampleFeature4", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "42")
    public void sampleFeatureRemaining() {
        setSelected(42);
        do {
            config.setSelected(new Random().nextInt());
        } while (config.getSelected() == 1 || config.getSelected() == 2);

        assertEquals("SampleFeatureRemaining", sampleFeature.test());
        assertEquals("SampleFeatureRemaining", sampleFeature.test());
    }

    @RepeatedTest(3)
    @SetSystemProperty(key = "feature", value = "33")
    public void genericSampleFeature2() {
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
    public void noSelectedFeature() {
        @SuppressWarnings("serial")
        GenericSampleFeature<StringBuilder> genericFeatureInstance = instance
                .select(new TypeLiteral<GenericSampleFeature<StringBuilder>>() {}).get();

        NoSelectedFeatureException exception = assertThrows(NoSelectedFeatureException.class,
                genericFeatureInstance::test);

        assertEquals(
                "No selected feature for io.github.jonasrutishauser.cdi.features.AbstractIT$GenericSampleFeature<java.lang.StringBuilder>",
                exception.getMessage());
        assertTrue(exception.getContextual() instanceof Bean);
    }

    @RepeatedTest(3)
    public void always() {
        AlwaysFeature.counter.set(0); // reset counter
        @SuppressWarnings("serial")
        StringBuffer result = instance.select(new TypeLiteral<GenericSampleFeature<StringBuffer>>() {}).get().test();

        assertEquals("always 0", result.toString());
    }

    @RepeatedTest(3)
    public void concurrency() {
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
    public void defaultScoped() {
        CharSequence result = instance.select(NotAFeature.class).get().test();

        assertEquals("default", result);
    }

    @ApplicationScoped
    static class Config {
        private int selected;
        private boolean destroyed;
        private boolean selectorDestroyed;
        private boolean feature3Created;

        public void setSelected(int selected) {
            this.selected = selected;
            destroyed = selectorDestroyed = feature3Created = false;
        }

        public int getSelected() {
            return selected;
        }

        public boolean isDestroyed() {
            return destroyed && selectorDestroyed;
        }

        public boolean isFeature3Created() {
            return feature3Created;
        }

        public void setDestroyed() {
            this.destroyed = true;
        }

        public void setSelectorDestroyed() {
            this.selectorDestroyed = true;
        }

        public void setFeature3Created() {
            this.feature3Created = true;
        }
    }

    static interface NotAFeature {
        CharSequence test();
    }

    static interface GenericSampleFeature<T extends CharSequence> extends NotAFeature {
        T test();
    }

    static interface SampleFeature extends GenericSampleFeature<String> {
    }

    @Dependent
    static class DefaultNotAFeature implements NotAFeature {
        @Override
        public CharSequence test() {
            return "default";
        }
    }

    @Dependent
    @Feature(selector = NeverSelector.class)
    @SuppressWarnings("rawtypes")
    static class NeverFeature implements GenericSampleFeature<StringBuilder>, ContextualSelector {
        @Override
        public StringBuilder test() {
            return fail("should not be called");
        }

        @Override
        public boolean selected(Context context) {
            return false;
        }
    }

    @Dependent
    @Feature
    static class AlwaysFeature implements GenericSampleFeature<StringBuffer>, ThrowableSelector {
        static AtomicInteger counter = new AtomicInteger();
        private final int id;

        public AlwaysFeature() {
            this.id = counter.getAndIncrement();
        }

        @Override
        public StringBuffer test() {
            return new StringBuffer("always " + id);
        }

        @Override
        public void valid() {
            // should always be valid
        }
    }

    @Dependent
    @Feature(cache = @Cache(durationMillisProperty = "cache.${type}.millis"))
    static class SampleFeature1 implements SampleFeature, Selector {
        private final Config config;

        @Inject
        SampleFeature1(Config config) {
            this.config = config;
        }

        @Override
        public String test() {
            return "SampleFeature1";
        }

        @Override
        public boolean selected() {
            return config.getSelected() == 1;
        }
    }

    @Dependent
    @Feature(selector = SampleFeature2Selector.class)
    static class SampleFeature2 implements SampleFeature {
        private final Config config;

        @Inject
        SampleFeature2(Config config) {
            this.config = config;
        }

        @Override
        public String test() {
            return "SampleFeature2";
        }

        @PreDestroy
        void destroy() {
            config.setDestroyed();
        }
    }

    @Dependent
    static class SampleFeature2Selector implements ContextualSelector<SampleFeature2> {
        private final Config config;

        @Inject
        SampleFeature2Selector(Config config) {
            this.config = config;
        }

        @Override
        public boolean selected(Context<? extends SampleFeature2> context) {
            assertEquals("SampleFeature2", context.instance().test());
            return config.getSelected() == 2;
        }

        @PreDestroy
        void destroy() {
            config.setSelectorDestroyed();
        }
    }

    @Dependent
    static class SampleFeature3 {

        private final Config config;

        @Inject
        public SampleFeature3(Config config) {
            this.config = config;
        }

        @ApplicationScoped
        @Feature(propertyKey = "feature", propertyValue = "3", cache = @Cache(durationMillis = 0, durationMillisProperty = "not.defined.property"))
        @Produces
        SampleFeature create() {
            config.setFeature3Created();
            return () -> "SampleFeature3";
        }
    }

    @ApplicationScoped
    @Feature(propertyKey = "feature4")
    static class SampleFeature4 implements SampleFeature {
        @Override
        public String test() {
            return "SampleFeature4";
        }
    }

    @Dependent
    @Feature(selector = NeverSelector.class)
    static class SampleFeatureNever implements SampleFeature {
        @Override
        public String test() {
            return "SampleFeature3";
        }
    }

    public static class NeverSelector implements ThrowableSelector {
        @Override
        public void valid() throws Exception {
            throw new IllegalStateException("never");
        }
    }

    @ApplicationScoped
    @Feature(remaining = true, cache = @Cache(durationMillis = 0))
    static class SampleFeatureRemaining implements SampleFeature {
        @Override
        public String test() {
            return "SampleFeatureRemaining";
        }
    }

}
