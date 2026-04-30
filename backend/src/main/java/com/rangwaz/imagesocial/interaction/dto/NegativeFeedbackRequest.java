package com.rangwaz.imagesocial.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NegativeFeedbackRequest(
        @NotBlank String feedbackType,
        @Size(max = 255) String reason
) {
}
