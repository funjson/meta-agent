package com.funjson.metaagent.control.application;

import java.util.UUID;

import com.funjson.metaagent.job.api.JobView;

/**
 * Describes a background execution request produced by a Control turn.
 *
 * <p>A mixed user turn can both resume an existing TaskRun and create a new Job.
 * The chat response still has one ControlDecision, while execution dispatches
 * are represented explicitly here so the ControlKernel can submit all required
 * background work after the initialization transaction commits.</p>
 *
 * @param jobId Job that should be started or resumed
 * @param jobVersion version expected when starting a Job
 * @param resumeTaskRunId TaskRun to resume; {@code null} means start Job
 */
public record ControlDispatchCommand(
        UUID jobId,
        long jobVersion,
        UUID resumeTaskRunId) {

    /**
     * Creates a start command for a newly runnable Job.
     *
     * @param job Job view
     * @return start dispatch command
     */
    public static ControlDispatchCommand start(JobView job) {
        return new ControlDispatchCommand(job.id(), job.version(), null);
    }

    /**
     * Creates a resume command for a TaskRun waiting inside a Job.
     *
     * @param job Job view
     * @param taskRunId TaskRun to resume
     * @return resume dispatch command
     */
    public static ControlDispatchCommand resume(
            JobView job,
            UUID taskRunId) {
        return new ControlDispatchCommand(job.id(), job.version(), taskRunId);
    }

    /**
     * @return whether this command resumes an existing TaskRun
     */
    public boolean resume() {
        return resumeTaskRunId != null;
    }
}
