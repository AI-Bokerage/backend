package com.company.audit.core.internal;

import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.core.internal.crypto.ChainHasher;
import com.company.audit.core.port.ChainRepository;
import java.util.List;

/**
 * Default implementation of {@link AuditVerifier}.
 *
 * <p>Not part of the public API — this class is public only so that {@link AuditVerifier#create}
 * can construct it from the {@code api} package; it is kept out of the public surface by not
 * being exported from this module's {@code module-info.java}. Consumers obtain instances via
 * {@link AuditVerifier#create}, never by naming this class.
 */
public final class DefaultAuditVerifier implements AuditVerifier {

    private final ChainRepository repository;
    private final ChainHasher chainHasher = new ChainHasher();

    /**
     * Creates a new verifier. Not part of the public API; use {@link AuditVerifier#create}.
     *
     * @param repository the storage port to read records from
     */
    public DefaultAuditVerifier(ChainRepository repository) {
        this.repository = repository;
    }

    @Override
    public VerificationResult verify(String partitionKey, PartitionContext partitionContext) {
        HashValue expectedPrev = chainHasher.computeGenesis(partitionContext);
        long expectedSeq = 1;

        List<ChainedRecord> records = repository.findAllOrderedBySeq(partitionKey);
        for (ChainedRecord record : records) {
            if (record.seq() != expectedSeq) {
                AuditChainStatus status = record.seq() < expectedSeq
                        ? AuditChainStatus.DUPLICATE_SEQ
                        : AuditChainStatus.BREAK_AT_SEQ;
                return VerificationResult.brokenAt(
                        partitionKey, status, record.seq(), "seq gap or duplicate");
            }
            if (!record.prevEventHash().equals(expectedPrev)) {
                return VerificationResult.brokenAt(
                        partitionKey, AuditChainStatus.BREAK_AT_SEQ, record.seq(),
                        "prev hash mismatch");
            }
            HashValue recomputed =
                    chainHasher.computeEventHash(record.prevEventHash(), record.payloadHash());
            if (!recomputed.equals(record.eventHash())) {
                return VerificationResult.brokenAt(
                        partitionKey, AuditChainStatus.HASH_MISMATCH, record.seq(),
                        "event hash mismatch");
            }
            expectedPrev = record.eventHash();
            expectedSeq++;
        }
        return VerificationResult.ok(partitionKey);
    }
}
