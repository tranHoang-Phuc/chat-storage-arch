package com.sds.phucth.chatstoragearch.repository;

import com.sds.phucth.chatstoragearch.models.MessageRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MessageRefRepo extends JpaRepository<MessageRef, String> {
    @Query(value = """
    select * from messages_ref
    where conversation_id = :cid and seq > :afterSeq
    order by seq asc
    offset 0 rows fetch next :limit rows only
    """, nativeQuery = true)
    List<MessageRef> pageAsc(@Param("cid") String cid, @Param("afterSeq") long afterSeq, @Param("limit") int limit);

    @Query(value = """
    select * from messages_ref
    where conversation_id = :cid and seq < :beforeSeq
    order by seq desc
    offset 0 rows fetch next :limit rows only
    """, nativeQuery = true)
    List<MessageRef> pageDesc(@Param("cid") String cid, @Param("beforeSeq") long beforeSeq, @Param("limit") int limit);

    @Query(value = "select * from messages_ref where ref_id like 'cas:%' and created_at < :cutoff order by conversation_id, seq", nativeQuery = true)
    List<MessageRef> findEligibleForCompaction(@Param("cutoff") OffsetDateTime cutoff);
}
