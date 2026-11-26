package dev.nightclient.api;

public final class ViewmodelSelection {
  public static final ViewmodelSelection NONE = new ViewmodelSelection("", null, 0);
  public final String hand;
  public final PickMask mask, mainMask, offMask;
  public final ViewmodelProjection projection, mainProjection, offProjection;
  public final long revision;
  public final float pivotX, pivotY;

  public ViewmodelSelection(String hand, PickMask mask, long revision) {
    this(hand, mask, revision, "main".equals(hand) ? mask : null, "off".equals(hand) ? mask : null);
  }

  public ViewmodelSelection(
      String hand, PickMask mask, long revision, PickMask mainMask, PickMask offMask) {
    this(hand, mask, revision, mainMask, offMask, null, null);
  }

  public ViewmodelSelection(String hand, PickMask mask, long revision, PickMask mainMask,
      PickMask offMask, ViewmodelProjection mainProjection, ViewmodelProjection offProjection) {
    if (!"main".equals(hand) && !"off".equals(hand) && !"".equals(hand))
      throw new IllegalArgumentException("Unknown viewmodel hand");
    this.hand = hand;
    this.mask = mask;
    this.mainMask = mainMask;
    this.offMask = offMask;
    this.mainProjection = mainProjection;
    this.offProjection = offProjection;
    this.projection = "off".equals(hand) ? offProjection : mainProjection;
    this.revision = revision;
    float[] pivot = projection == null ? null : projection.pivot();
    this.pivotX = pivot == null ? (mask == null ? 0 : mask.centerX) : pivot[0];
    this.pivotY = pivot == null ? (mask == null ? 0 : mask.centerY) : pivot[1];
  }

  public static ViewmodelSelection missed(long revision) {
    return new ViewmodelSelection("", null, revision);
  }

  public ViewmodelSelection forHand(String wanted) {
    PickMask chosen = "off".equals(wanted) ? offMask : mainMask;
    return new ViewmodelSelection(chosen == null || chosen.isEmpty() ? "" : wanted, chosen,
        revision, mainMask, offMask, mainProjection, offProjection);
  }

  public boolean selected() {
    return mask != null && !mask.isEmpty() && !hand.isEmpty();
  }
}
