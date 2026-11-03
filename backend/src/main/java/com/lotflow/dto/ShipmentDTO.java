package com.lotflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 虚拟发货请求。 */
public record ShipmentDTO(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNo
) {
}
