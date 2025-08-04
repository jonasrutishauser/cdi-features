package io.github.jonasrutishauser.cdi.features.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import io.github.jonasrutishauser.cdi.features.NoSelectedFeatureException;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.Bean;

class Cache {

    public enum Selection {
        SELECTED, NOT_SELECTED, REMAINING;

        public static Selection of(boolean selected) {
            return selected ? SELECTED : NOT_SELECTED;
        }
    }

    private final ConcurrentMap<Contextual<?>, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public <T> Bean<?> compute(Contextual<T> key, Collection<Bean<? extends T>> items,
            Function<Bean<? extends T>, Selection> selection, ToLongFunction<Bean<? extends T>> cacheDurationInMillis) {
        CacheEntry entry = entries.compute(key, (k, current) -> {
            if (current != null && (current.forever || !current.validUntil.isBefore(Instant.now(clock)))) {
                return current;
            }
            Bean<? extends T> remaining = null;
            for (Bean<? extends T> item : items) {
                Selection selected = selection.apply(item);
                if (selected == Selection.REMAINING) {
                    remaining = item;
                } else if (selected == Selection.SELECTED) {
                    return new CacheEntry(item, validUntil(cacheDurationInMillis.applyAsLong(item)));
                }
            }
            if (remaining == null) {
                throw new NoSelectedFeatureException(k);
            }
            return new CacheEntry(remaining, validUntil(cacheDurationInMillis.applyAsLong(remaining)));
        });
        if (entry.validUntil.isBefore(Instant.now(clock))) {
            entries.remove(key, entry);
        }
        return entry.target;
    }

    private Instant validUntil(long durationInMillis) {
        if (durationInMillis < 0) {
            return Instant.MAX;
        } else if (durationInMillis == 0) {
            return Instant.MIN;
        }
        return Instant.now(clock).plusMillis(durationInMillis);
    }

    private static class CacheEntry {
        final Bean<?> target;
        final Instant validUntil;
        final boolean forever;

        public CacheEntry(Bean<?> target, Instant validUntil) {
            this.target = target;
            this.validUntil = validUntil;
            this.forever = validUntil.equals(Instant.MAX);
        }
    }

}
