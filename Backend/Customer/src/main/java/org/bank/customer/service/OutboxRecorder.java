package org.bank.customer.service;

import lombok.RequiredArgsConstructor;
import org.bank.customer.model.OutboxEvent;
import org.bank.customer.repository.OutboxEventRepository;
import org.bank.common.security.correlation.CorrelationIds;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Writes domain events to the outbox table. Requires an active transaction so an event can never exist
 * without the business change it describes (and vice versa).
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxEventRepository outboxEvents;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        outboxEvents.save(OutboxEvent.create(aggregateType, aggregateId, eventType,
                jsonMapper.writeValueAsString(payload), CorrelationIds.current(), OffsetDateTime.now(clock)));
    }
}
