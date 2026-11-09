package com.itsluminous.verysmarttv.testkit.hero;

import com.itsluminous.verysmarttv.testkit.FakeHeroProvider;
import com.itsluminous.verysmarttv.testkit.HeroFixture;

/**
 * {@link FakeHeroProvider} seeded with {@link HeroFixture} when the system creates it: the stand-in companion's
 * only component. The launcher queries it like the real provider, through the signature permission, and gets the
 * three fixture titles and their backdrops.
 */
public final class FixtureHeroProvider extends FakeHeroProvider {
    @Override
    public boolean onCreate() {
        HeroFixture.seed();
        return super.onCreate();
    }
}
