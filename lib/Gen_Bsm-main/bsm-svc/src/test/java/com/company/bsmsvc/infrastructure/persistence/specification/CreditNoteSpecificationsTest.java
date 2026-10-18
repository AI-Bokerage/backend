package com.company.bsmsvc.infrastructure.persistence.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

@SuppressWarnings("unchecked")
class CreditNoteSpecificationsTest {

    @Test
    void fromFilter_withNullFilter_returnsConjunction() {
        Specification<CreditNoteEntity> spec = CreditNoteSpecifications.fromFilter(null);
        assertThat(spec).isNotNull();

        Root<CreditNoteEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = spec.toPredicate(root, query, cb);
        assertThat(result).isSameAs(conjunction);
    }

    @Test
    void fromFilter_withTenantId_callsEqualOnTenantId() {
        UUID tenantId = UUID.randomUUID();
        CreditNoteFilter filter = new CreditNoteFilter(tenantId, null, null, null, null, null);
        Specification<CreditNoteEntity> spec = CreditNoteSpecifications.fromFilter(filter);

        Root<CreditNoteEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("tenantId")).thenReturn(path);
        when(cb.equal(path, tenantId)).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).equal(path, tenantId);
    }

    @Test
    void fromFilter_withStatus_callsEqualOnStatus() {
        CreditNoteFilter filter = new CreditNoteFilter(null, null, CreditNoteStatus.OPEN, null, null, null);
        Specification<CreditNoteEntity> spec = CreditNoteSpecifications.fromFilter(filter);

        Root<CreditNoteEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("status")).thenReturn(path);
        when(cb.equal(path, CreditNoteStatus.OPEN)).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(cb).equal(path, CreditNoteStatus.OPEN);
    }

    @Test
    void fromFilter_withDateRange_callsGreaterAndLessThan() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        CreditNoteFilter filter = new CreditNoteFilter(null, null, null, null, from, to);
        Specification<CreditNoteEntity> spec = CreditNoteSpecifications.fromFilter(filter);

        Root<CreditNoteEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Instant> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("createdAt")).thenReturn((Path) path);
        when(cb.greaterThanOrEqualTo(any(), (Instant) any())).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(), (Instant) any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(any(), (Instant) any());
        verify(cb).lessThanOrEqualTo(any(), (Instant) any());
    }

    @Test
    void fromFilter_emptyFilter_returnsConjunction() {
        CreditNoteFilter filter = new CreditNoteFilter(null, null, null, null, null, null);
        Specification<CreditNoteEntity> spec = CreditNoteSpecifications.fromFilter(filter);

        Root<CreditNoteEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = spec.toPredicate(root, query, cb);
        assertThat(result).isSameAs(conjunction);
    }
}
