package com.funjson.metaagent.runtime.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.runtime.domain.TaskIntentScope;

/**
 * Read port for task intent scopes persisted on Job effective policy snapshots.
 */
public interface TaskIntentScopeStore {

    /**
     * Finds the task intent scope attached to a Job.
     *
     * @param jobId Job ID
     * @return persisted scope, or empty for historical Jobs
     */
    Optional<TaskIntentScope> findByJobId(UUID jobId);
}
