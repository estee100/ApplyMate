package com.applymate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** The system clock plus an adjustable offset, so tests can move time forward. */
public class MutableClock extends Clock {

    private final Clock base = Clock.systemDefaultZone();
    private volatile Duration offset = Duration.ZERO;

    public void advance(Duration by) {
        offset = offset.plus(by);
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return base.withZone(zone);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset);
    }
}
