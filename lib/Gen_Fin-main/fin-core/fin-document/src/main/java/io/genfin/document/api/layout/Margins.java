package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public final class Margins {

  private final int topPoints;
  private final int bottomPoints;
  private final int leftPoints;
  private final int rightPoints;

  private Margins(int topPoints, int bottomPoints, int leftPoints, int rightPoints) {
    this.topPoints = Validate.nonNegative(topPoints, "topPoints must not be negative");
    this.bottomPoints = Validate.nonNegative(bottomPoints, "bottomPoints must not be negative");
    this.leftPoints = Validate.nonNegative(leftPoints, "leftPoints must not be negative");
    this.rightPoints = Validate.nonNegative(rightPoints, "rightPoints must not be negative");
  }

  public static Margins of(int topPoints, int bottomPoints, int leftPoints, int rightPoints) {
    return new Margins(topPoints, bottomPoints, leftPoints, rightPoints);
  }

  public static Margins uniform(int allSides) {
    return new Margins(allSides, allSides, allSides, allSides);
  }

  public static Margins none() {
    return new Margins(0, 0, 0, 0);
  }

  public int topPoints() {
    return topPoints;
  }

  public int bottomPoints() {
    return bottomPoints;
  }

  public int leftPoints() {
    return leftPoints;
  }

  public int rightPoints() {
    return rightPoints;
  }
}
