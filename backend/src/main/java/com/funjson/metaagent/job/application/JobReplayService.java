package com.funjson.metaagent.job.application;

import java.util.List;

import com.funjson.metaagent.job.application.port.out.JobStore;
import com.funjson.metaagent.job.domain.JobReplayCandidate;
import org.springframework.stereotype.Service;

/**
 * Queries Jobs that need persistent worker replay.
 */
@Service
public class JobReplayService {

    private final JobStore jobStore;

    /**
     * Creates a Job replay service.
     *
     * @param jobStore Job persistence port
     */
    public JobReplayService(JobStore jobStore) {
        this.jobStore = jobStore;
    }

    /**
     * Finds Jobs that were committed but never dispatched into TaskRuns.
     *
     * @param limit max candidates
     * @return startable Job candidates
     */
    public List<JobReplayCandidate> findStartableJobs(int limit) {
        return jobStore.findStartableJobsForReplay(limit);
    }
}
