package com.funjson.metaagent.profile.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.profile.application.port.out.SubagentProfileStore;
import com.funjson.metaagent.profile.domain.SubagentProfile;
import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SubagentProfileStore 的 MyBatis 适配器。
 */
@Repository
public class SubagentProfileRepository
        implements SubagentProfileStore {

    private final SubagentProfilePersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 SubagentProfile Repository。
     *
     * @param mapper MyBatis Mapper
     * @param objectMapper JSON Mapper
     */
    public SubagentProfileRepository(
            SubagentProfilePersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    public Optional<SubagentProfile> find(SubagentProfileRef ref) {
        return Optional.ofNullable(
                        mapper.find(ref.id(), ref.version()))
                .map(this::map);
    }

    /** {@inheritDoc} */
    public List<SubagentProfile> findAll(String agentProfileId) {
        return mapper.findAll(agentProfileId).stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public void insert(SubagentProfile profile) {
        mapper.insert(
                profile.ref().id(),
                profile.agentProfileId(),
                profile.ref().version(),
                profile.name(),
                profile.rolePrompt(),
                json(profile.modelPolicy()),
                json(profile.skillRefs()),
                json(profile.toolAllowlist()),
                json(profile.requestedAuthority()),
                profile.status());
    }

    /** 转换数据库行。 */
    private SubagentProfile map(Map<String, Object> row) {
        try {
            return new SubagentProfile(
                    new SubagentProfileRef(
                            text(row, "id"),
                            number(row, "version").intValue()),
                    text(row, "agentProfileId"),
                    text(row, "name"),
                    text(row, "rolePrompt"),
                    objectMapper.readValue(
                            text(row, "modelPolicyJson"),
                            new TypeReference<Map<String, Object>>() { }),
                    objectMapper.readValue(
                            text(row, "skillRefsJson"),
                            new TypeReference<List<CapabilityRef>>() { }),
                    objectMapper.readValue(
                            text(row, "toolAllowlistJson"),
                            new TypeReference<Set<String>>() { }),
                    objectMapper.readValue(
                            text(row, "authorityJson"),
                            AuthorityEnvelope.class),
                    text(row, "status"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to read SubagentProfile",
                    exception);
        }
    }

    /** 序列化 JSON。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize SubagentProfile",
                    exception);
        }
    }

    /** 读取文本。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取数值。 */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }
}
