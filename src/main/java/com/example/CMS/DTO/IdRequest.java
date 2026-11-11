package com.example.CMS.DTO;

import jakarta.validation.constraints.NotBlank;

public class IdRequest {
    @NotBlank
    private String id;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
