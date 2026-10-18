export interface VisibilityCheckParams {
  tenantId: string;
  roleId?: string;
  entityType: string;
  entityId: string;
}

export interface IVisibilityFilter {
  isVisible(params: VisibilityCheckParams): boolean | Promise<boolean>;
}

// Default when no host-supplied filter is configured — returns every
// tenant-scoped result to any caller holding a valid internal secret for
// that tenant. See docs/source-audit-notes.md's "Role-based ACL" section
// before relying on this for any entity type with real per-role visibility
// requirements — it is a permissive default, not a safe one.
export class AllowAllVisibilityFilter implements IVisibilityFilter {
  isVisible(): true {
    return true;
  }
}
