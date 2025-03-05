package io.github.jonasrutishauser.cdi.features.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

class CdiFeaturesIT {

    @RegisterExtension
    static final QuarkusUnitTest quarkusConfig = new QuarkusUnitTest() //
            .setFlatClassPath(true) // needed for invoker
            .withApplicationRoot(archive -> archive //
                    .addClasses(Config.class, GenericSampleFeature.class, SampleFeature.class, SampleFeature1.class,
                            SampleFeature2.class, SampleFeature2Selector.class, SampleFeature3.class,
                            SampleFeatureNever.class, NeverSelector.class, SampleFeatureRemaining.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            ).overrideConfigKey("quarkus.log.category.\"io.quarkus.arc\".level", "DEBUG");

    @Inject
    Config config;

    @Inject
    SampleFeature sampleFeature;

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

}
