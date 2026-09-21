package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByUserIdAndEndpointAndIdempotencyKey(UUID userId, String endpoint, String key);

    /**
     * Drops records older than the retention window, which is what keeps this
     * table from growing forever. ADR-008 named the growth as an accepted
     * consequence and this sweep as the natural home for the prune.
     *
     * <p>{@code createdAt} is the only predicate, and it is load-bearing in the
     * other direction: a key deleted while its client might still retry stops
     * replaying and writes a second booking. The window has to stay comfortably
     * longer than any client's retry horizon.
     *
     * <p>Annotated {@code @Transactional} because its only caller is
     * deliberately not: {@code BookingExpiryService} keeps each booking's expiry
     * in a transaction of its own, so this write needs one here or it inherits
     * Spring Data's class-level {@code readOnly} transaction and fails.
     */
    @Transactional
    @Modifying
    @Query("delete from IdempotencyKey key where key.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
