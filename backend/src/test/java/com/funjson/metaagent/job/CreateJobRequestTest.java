package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import com.funjson.metaagent.job.api.CreateJobRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 验证创建 Job 请求的 Bean Validation 合同。
 */
class CreateJobRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankRequest() {
        Set<ConstraintViolation<CreateJobRequest>> violations =
                validator.validate(new CreateJobRequest("  ", "fake"));

        assertThat(violations).isNotEmpty();
    }
}
