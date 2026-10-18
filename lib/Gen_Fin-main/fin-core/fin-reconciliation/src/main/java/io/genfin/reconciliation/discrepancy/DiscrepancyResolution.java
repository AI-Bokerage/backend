package io.genfin.reconciliation.discrepancy;

/** How a {@link Discrepancy} was — or has not yet been — dealt with. */
public enum DiscrepancyResolution {

  /** Not yet looked at. Every newly detected discrepancy starts here. */
  UNRESOLVED,

  /** Reviewed and found to be within acceptable bounds; no correction made. */
  ACCEPTED,

  /** A correcting entry was made to bring the two sides back in line. */
  ADJUSTED,

  /** Reviewed and deliberately left as-is (e.g. amount too small to pursue). */
  WRITTEN_OFF,

  /** Handed to a human/process outside reconciliation for a decision. */
  ESCALATED,

  /** Resolved by policy without human input (e.g. it fell back within tolerance on a re-run). */
  AUTO_RESOLVED
}
