package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前用户可自行修改的公开资料。 */
public record UpdateProfileDTO(@NotBlank @Size(max = 80) String displayName) {
}
