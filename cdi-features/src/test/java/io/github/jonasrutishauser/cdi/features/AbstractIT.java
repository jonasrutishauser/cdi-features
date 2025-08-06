package io.github.jonasrutishauser.cdi.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junitpioneer.jupiter.SetSystemProperty;

import io.github.jonasrutishauser.cdi.features.Feature.Cache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
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
    public void sampleFeature1() {
        setSelected(1);

        assertEquals("SampleFeature1", sampleFeature.test());
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
        assertEquals("SampleFeature2", genericFeatureInstance.get().test());
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

    static interface GenericSampleFeature<T extends CharSequence> {
        T test();
    }

    static interface SampleFeature extends GenericSampleFeature<String> {
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

    @ApplicationScoped
    @Feature(propertyKey = "feature", propertyValue = "3", cache = @Cache(durationMillis = 0))
    static class SampleFeature3 implements SampleFeature {
        SampleFeature3() {
        }

        @Inject
        SampleFeature3(Config config) {
            config.setFeature3Created();
        }

        @Override
        public String test() {
            return "SampleFeature3";
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
    @Feature(remaining = true)
    static class SampleFeatureRemaining implements SampleFeature {
        @Override
        public String test() {
            return "SampleFeatureRemaining";
        }
    }

}
