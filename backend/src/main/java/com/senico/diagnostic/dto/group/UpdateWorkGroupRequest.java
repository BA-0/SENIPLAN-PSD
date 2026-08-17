package com.senico.diagnostic.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkGroupRequest(
        @NotBlank(message = "Le nom du groupe est requis")
        @Size(max = 150)
        String name,

        String description,

        @Size(max = 150)
        String leaderFullName,

        Boolean enabled
) {
}
