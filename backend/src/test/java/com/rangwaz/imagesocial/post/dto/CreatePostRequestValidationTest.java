package com.rangwaz.imagesocial.post.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreatePostRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void assetsMayBeEmptyForTextPosts() {
        CreatePostRequest emptyAssetsRequest = new CreatePostRequest(
                "纯文本",
                "纯文本发布不需要图片资产",
                "general",
                List.of("日常记录"),
                List.of()
        );
        CreatePostRequest omittedAssetsRequest = new CreatePostRequest(
                "纯文本",
                "前端没有图片时可以省略 assets 字段",
                "general",
                List.of("日常记录"),
                null
        );

        assertThat(validator.validate(emptyAssetsRequest))
                .noneMatch(violation -> "assets".equals(violation.getPropertyPath().toString()));
        assertThat(validator.validate(omittedAssetsRequest))
                .noneMatch(violation -> "assets".equals(violation.getPropertyPath().toString()));
    }
}
