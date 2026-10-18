package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.id.DunningCaseId;

public record DunningCaseStageAdvanced(
    EventMetadata metadata,
    DunningCaseId caseId,
    CollectionStage fromStage,
    CollectionStage toStage)
    implements DomainEvent {}
