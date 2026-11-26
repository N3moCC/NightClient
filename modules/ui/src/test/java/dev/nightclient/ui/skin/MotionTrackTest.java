package dev.nightclient.ui.skin;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
class MotionTrackTest {
  @Test
  void frameRateDoesNotChangeTheCurveOrItsEndpoint() {
    for (String easing : new String[] {"linear", "cubic-in", "cubic-out", "cubic-in-out",
             "quad-out", "quart-out", "quint-out", "sine-out", "expo-out", "back-out", "smooth"}) {
      MotionTrack a = new MotionTrack(0), b = new MotionTrack(0);
      a.to(1, 0, .3f, easing, false);
      b.to(1, 0, .3f, easing, false);
      for (int i = 1; i < 24; i++) a.to(1, i / 80d, .3f, easing, false);
      for (int i = 1; i < 72; i++) b.to(1, i / 240d, .3f, easing, false);
      assertEquals(a.to(1, .15, .3f, easing, false), b.to(1, .15, .3f, easing, false), .00001f);
      assertEquals(1, a.to(1, .31, .3f, easing, false));
      assertEquals(1, b.to(1, .31, .3f, easing, false));
    }
  }
  @Test
  void settledTracksKeepExactEndpointsAndCanStartAnotherTransition() {
    MotionTrack track = new MotionTrack(37);
    assertEquals(37, track.at(10));
    track.to(-12, 10, .5f, "back-out", false);
    assertEquals(37, track.at(9));
    assertEquals(37, track.at(10));
    assertEquals(-12, track.at(10.5));
    assertEquals(-12, track.at(100));
    assertFalse(track.inFlight(100));
    assertEquals(-12, track.to(5, 101, .2f, "linear", false));
    assertEquals(-3.5f, track.at(101.1), .0001f);
  }
  @Test
  void everyCurveHasExactEndpointsWithoutChangingItsInterior() {
    for (String easing : new String[] {"linear", "cubic-in", "cubic-out", "cubic-in-out",
             "quad-out", "quart-out", "quint-out", "sine-out", "expo-out", "back-out", "smooth"}) {
      assertEquals(0, MotionTrack.ease(0, easing));
      assertEquals(1, MotionTrack.ease(1, easing));
      assertEquals(0, MotionTrack.ease(-1, easing));
      assertEquals(1, MotionTrack.ease(2, easing));
    }
    assertEquals(.875f, MotionTrack.ease(.5f, "cubic-out"));
    assertEquals(.5f, MotionTrack.ease(.5f, "linear"));
  }
  @Test
  void reversingStartsFromCurrentVisualValueAndReducedMotionSnaps() {
    MotionTrack motion = new MotionTrack(0);
    motion.to(1, 0, .2f, "linear", false);
    assertEquals(.5f, motion.to(0, .1, .2f, "linear", false), .0001f);
    assertEquals(.25f, motion.to(0, .2, .2f, "linear", false), .0001f);
    assertEquals(1, motion.to(1, .21, .2f, "linear", true));
  }
}
