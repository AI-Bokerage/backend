package io.cpms.common.messaging;

public final class CpmsMessagingConstants {

    private CpmsMessagingConstants() {}

    // ── Shared Exchanges ─────────────────────────────────────────────────────
    public static final String CPMS_EVENTS_EXCHANGE  = "cpms.events";
    public static final String NOTIF_INBOUND_EXCHANGE = "notification.exchange";

    // ── Dead-Letter Exchange for CPMS shared queues ──────────────────────────
    public static final String CPMS_EVENTS_DLX = "cpms.events.dlx";

    // ── Shared Routing Keys (cross-service) ──────────────────────────────────
    public static final String TENANT_CREATED    = "tenant.created";
    public static final String TENANT_SUSPENDED  = "tenant.suspended";
    public static final String TENANT_REACTIVATED = "tenant.reactivated";

    // ── ADM-SVC: Exchanges & Queues ──────────────────────────────────────────
    public static final String QUEUE_TENANT_CREATED    = "q.adm.tenant-created";
    public static final String QUEUE_TENANT_SUSPENDED  = "q.adm.tenant-suspended";
    public static final String QUEUE_TENANT_REACTIVATED = "q.adm.tenant-reactivated";
    public static final String QUEUE_TENANT_CANCELLED  = "q.adm.tenant-cancelled";

    public static final String QUEUE_CPT_CLIENT_USER_CREATED     = "q.adm.cpt.client-user-created";
    public static final String QUEUE_CPT_CLIENT_USER_UPDATED     = "q.adm.cpt.client-user-updated";
    public static final String QUEUE_CPT_CLIENT_USER_DEACTIVATED = "q.adm.cpt.client-user-deactivated";

    // ── ADM-SVC: Routing Keys ────────────────────────────────────────────────
    public static final String USER_CREATED                  = "adm.user.created";
    public static final String USER_DEACTIVATED              = "adm.user.deactivated";
    public static final String USER_REACTIVATED              = "adm.user.reactivated";
    public static final String USER_OFFBOARDING_INITIATED    = "adm.user.offboarding.initiated";
    public static final String USER_OFFBOARDED               = "adm.user.offboarded";
    public static final String USER_PRESENCE_CHANGED          = "adm.user.presence.changed";
    public static final String ROLE_ASSIGNED           = "role.assigned";
    public static final String ROLE_REMOVED            = "role.removed";
    public static final String ROLE_PERMISSION_UPDATED = "role.permission.updated";
    public static final String TENANT_CANCELLED        = "tenant.cancelled";
    public static final String CLIENT_CREATED          = "client.created";
    public static final String SUPPORT_TICKET_REQUESTED = "support.ticket.requested";

    // ── TNT-SVC: Exchanges ───────────────────────────────────────────────────
    public static final String TENANT_EVENTS_EXCHANGE = "tnt.events.exchange";
    public static final String TENANT_EVENTS_DLX      = "tnt.events.exchange.dlx";

    // ── TNT-SVC: Queues ──────────────────────────────────────────────────────
    public static final String PROVISIONING_START_QUEUE = "tnt.provisioning.start.queue";
    public static final String PROVISIONING_RETRY_QUEUE = "tnt.provisioning.retry.queue";
    public static final String PROVISIONING_DLQ        = "tnt.provisioning.dlq";
    public static final String NOTIFICATIONS_QUEUE     = "tnt.notifications.queue";
    public static final String TENANT_CREATION_QUEUE   = "q.tnt.tenant-create";
    public static final String TENANT_CREATION_DLQ     = "q.dlq.q.tnt.tenant-create";
    public static final String EXPORT_REQUESTED_QUEUE  = "q.tnt.export.requested";
    public static final String EXPORT_APPROVED_QUEUE   = "q.tnt.export.approved";
    public static final String DLQ_EXPORT_APPROVED      = "q.dlq.tnt.export.approved";
    public static final String QUEUE_TNT_ONBOARDING_CHECKLIST = "q.tnt.onboarding-checklist";

    // ── ADM bootstrap step (tnt-svc → adm-svc → tnt-svc) ───────────────────
    public static final String QUEUE_ADM_BOOTSTRAP_REQUEST   = "q.adm.bootstrap.request";
    public static final String QUEUE_TNT_ADM_BOOTSTRAP_REPLY = "q.tnt.adm-bootstrap.reply";
    public static final String ADM_BOOTSTRAP_REQUESTED  = "adm.bootstrap.requested";
    public static final String ADM_BOOTSTRAP_COMPLETED  = "adm.bootstrap.completed";
    public static final String ADM_BOOTSTRAP_FAILED     = "adm.bootstrap.failed";

    // ── TBR bootstrap step (tnt-svc → tbr-svc → tnt-svc) ───────────────────
    public static final String QUEUE_TBR_BOOTSTRAP_REQUEST   = "q.tbr.bootstrap.request";
    public static final String QUEUE_TNT_TBR_BOOTSTRAP_REPLY = "q.tnt.tbr-bootstrap.reply";
    public static final String TBR_BOOTSTRAP_REQUESTED  = "tbr.bootstrap.requested";
    public static final String TBR_BOOTSTRAP_COMPLETED  = "tbr.bootstrap.completed";
    public static final String TBR_BOOTSTRAP_FAILED     = "tbr.bootstrap.failed";

    // ── AUTH bootstrap step (tnt-svc → auth-svc → tnt-svc) ─────────────────
    public static final String QUEUE_AUTH_BOOTSTRAP_REQUEST   = "q.auth.bootstrap.request";
    public static final String QUEUE_TNT_AUTH_BOOTSTRAP_REPLY = "q.tnt.auth-bootstrap.reply";
    public static final String AUTH_BOOTSTRAP_REQUESTED  = "auth.bootstrap.requested";
    public static final String AUTH_BOOTSTRAP_COMPLETED  = "auth.bootstrap.completed";
    public static final String AUTH_BOOTSTRAP_FAILED     = "auth.bootstrap.failed";

    // ── DLQ queues for provisioning bootstrap queues ─────────────────────────
    public static final String DLQ_ADM_BOOTSTRAP_REQUEST    = "q.dlq.adm.bootstrap.request";
    public static final String DLQ_TBR_BOOTSTRAP_REQUEST    = "q.dlq.tbr.bootstrap.request";
    public static final String DLQ_AUTH_BOOTSTRAP_REQUEST   = "q.dlq.auth.bootstrap.request";
    public static final String DLQ_TNT_ADM_BOOTSTRAP_REPLY  = "q.dlq.tnt.adm-bootstrap.reply";
    public static final String DLQ_TNT_TBR_BOOTSTRAP_REPLY  = "q.dlq.tnt.tbr-bootstrap.reply";
    public static final String DLQ_TNT_AUTH_BOOTSTRAP_REPLY = "q.dlq.tnt.auth-bootstrap.reply";

    // ── TNT-SVC: Routing Keys ────────────────────────────────────────────────
    public static final String TENANT_PROVISIONING_INITIATED  = "tenant.provisioning.started";
    public static final String TENANT_PROVISIONING_RETRY      = "tenant.provisioning.retry";
    public static final String TENANT_PROVISIONING_FAILED     = "tenant.provisioning.failed";
    public static final String TENANT_PROVISIONING_COMPLETED  = "tenant.provisioning.completed";
    public static final String REGISTRATION_TENANT_REQUESTED  = "registration.tenant.requested";
    public static final String TENANT_ROLLBACK_STARTED        = "tenant.rollback.started";
    public static final String TENANT_ROLLBACK_COMPLETED      = "tenant.rollback.completed";
    public static final String TENANT_STATUS_CHANGED          = "tenant.status.changed";
    public static final String TENANT_EXPORT_REQUESTED        = "tenant.export.requested";
    public static final String TENANT_EXPORT_APPROVED         = "tenant.export.approved";

    // BSM-SVC's own subscription/add-on lifecycle routing keys moved to
    // com.company.bsmsvc.messaging.BsmMessagingRouting (Phase 2.7) — a shared platform library
    // has no business holding one service's private outbound wire vocabulary.

    // ── BSM-SVC inbound — consumes tenant.created from TNT-SVC ──────────────
    public static final String QUEUE_BSM_TENANT_CREATED     = "q.bsm.tenant-created";
    public static final String QUEUE_DLQ_BSM_TENANT_CREATED = "q.dlq.bsm.tenant-created";

    // ── TNT-SVC inbound — consumes subscription events from BSM-SVC ─────────
    public static final String QUEUE_TNT_BSM_SUBSCRIPTION     = "q.tnt.bsm-subscription";
    public static final String QUEUE_DLQ_TNT_BSM_SUBSCRIPTION = "q.dlq.tnt.bsm-subscription";

    // ── AUTH-SVC inbound — user lifecycle and role sync events from ADM-SVC ────
    public static final String QUEUE_AUTH_USER_CREATED             = "q.auth.user-created";
    public static final String QUEUE_AUTH_USER_DEACTIVATED         = "q.auth.user-deactivated";
    public static final String QUEUE_AUTH_USER_REACTIVATED         = "q.auth.user-reactivated";
    public static final String QUEUE_AUTH_ROLE_ASSIGNED            = "q.auth.role-assigned";
    public static final String QUEUE_AUTH_ROLE_REMOVED             = "q.auth.role-removed";
    public static final String QUEUE_AUTH_ROLE_PERMISSION_UPDATED  = "q.auth.role-permission-updated";
    public static final String QUEUE_AUTH_CLIENT_CREATED           = "q.auth.client-created";

    public static final String DLQ_AUTH_USER_CREATED             = "q.dlq.auth.user-created";
    public static final String DLQ_AUTH_USER_DEACTIVATED         = "q.dlq.auth.user-deactivated";
    public static final String DLQ_AUTH_USER_REACTIVATED         = "q.dlq.auth.user-reactivated";
    public static final String DLQ_AUTH_ROLE_ASSIGNED            = "q.dlq.auth.role-assigned";
    public static final String DLQ_AUTH_ROLE_REMOVED             = "q.dlq.auth.role-removed";
    public static final String DLQ_AUTH_ROLE_PERMISSION_UPDATED  = "q.dlq.auth.role-permission-updated";
    public static final String DLQ_AUTH_CLIENT_CREATED           = "q.dlq.auth.client-created";

    // ── PMT-SVC inbound — user deactivation from ADM-SVC ────────────────────
    public static final String QUEUE_PMT_USER_DEACTIVATED  = "q.pmt.user-deactivated";
    public static final String DLQ_PMT_USER_DEACTIVATED    = "q.dlq.pmt.user-deactivated";

    // ── Retry Config ─────────────────────────────────────────────────────────
    public static final int RETRY_QUEUE_TTL_MS  = 30_000;
    public static final int MAX_RETRY_ATTEMPTS  = 3;
}
