package com.senico.diagnostic.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWorkGroupRequest(
        @NotBlank(message = "Le nom du groupe est requis")
        @Size(max = 150)
        String name,

        String description,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit etre au format hexadecimal #RRGGBB")
        String color,

        @Size(max = 150)
        String leaderFullName,

        Boolean enabled
) {
}
