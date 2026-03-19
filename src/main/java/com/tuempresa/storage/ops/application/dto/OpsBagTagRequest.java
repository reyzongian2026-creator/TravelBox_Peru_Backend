package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OpsBagTagRequest(
        @Min(value = 1, message = "Debe existir al menos un bulto.")
        @Max(value = 20, message = "No se admiten mas de 20 bultos por operacion.")
        Integer bagUnits
) {
}
