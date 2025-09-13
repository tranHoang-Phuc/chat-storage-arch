package com.sds.phucth.chatstoragearch.repository;

import com.sds.phucth.chatstoragearch.models.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
