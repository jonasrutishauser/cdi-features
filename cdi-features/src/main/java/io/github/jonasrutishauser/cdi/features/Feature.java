package io.github.jonasrutishauser.cdi.features;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Feature {

    /**
     * @return selector class which will be lookup up as a CDI bean if possible
     *         (needs a public no-arg constructor otherwise).
     */
    @Nonbinding
    Class<? extends ContextualSelector<?>> selector() default Selector.class;

    /**
     * @return property key used for the selection. This requires that MicroProfile
     *         Config is available.
     */
    @Nonbinding
    String propertyKey() default "";

    /**
     * @return property value which is expected for the selection. If not specified
     *         any non null value is accepted.
     */
    @Nonbinding
    String propertyValue() default "";

    /**
     * @return {@code true} if it should be used if no other bean is selected. There
     *         can be at most one for a given type.
     */
    @Nonbinding
    boolean remaining() default false;

    /**
     * @return the cache configuration if this bean is selected.
     */
    @Nonbinding
    Cache cache() default @Cache;

    @Target({})
    @interface Cache {
        /**
         * @return the cache duration in milliseconds. The value {@code 0} means no
         *         caching and any negative value means forever.
         */
        long durationMillis() default -1;

        /**
         * @return the property key which defines the duration. It is possible to use
         *         <code>@{type}</code> to reference the injected type.
         * @see #durationMillis()
         */
        String durationMillisProperty() default "";
    }
}
