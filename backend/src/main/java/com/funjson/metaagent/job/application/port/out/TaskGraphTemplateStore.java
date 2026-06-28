package com.funjson.metaagent.job.application.port.out;

import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.domain.TaskGraphPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TaskGraphTemplate 版本持久化端口。
 */
public interface TaskGraphTemplateStore {

    /** @return 同一 Profile/Key 已存在的模板 ID */
    Optional<UUID> findTemplateId(
            String agentProfileId,
            String templateKey);

    /** @return 下一个版本号 */
    int nextVersion(String agentProfileId, String templateKey);

    /** 将同 Key 的当前激活版本退役。 */
    void retireActiveVersions(
            String agentProfileId,
            String templateKey);

    /** 插入不可变模板版本。 */
    void insert(
            UUID id,
            String agentProfileId,
            String templateKey,
            int version,
            String name,
            List<String> intentLabels,
            TaskGraphPlan graph,
            String checksum);

    /** @return 指定模板版本 */
    Optional<TaskGraphTemplateView> find(
            UUID id,
            int version);

    /** @return Profile 下全部模板版本 */
    List<TaskGraphTemplateView> findAll(String agentProfileId);

    /** @return Profile 下当前激活模板 */
    List<TaskGraphTemplateView> findActive(String agentProfileId);
}
