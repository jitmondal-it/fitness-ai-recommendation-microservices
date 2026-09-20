package com.fitness.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Password is required")
    @Email(message = "Invalid email format ")
    private String email;
    private String keycloakId;

    @NotBlank(message = "Password id required")
    @Size(min = 6, message = "password must have atleast 6 character")
    private String password;
    private String firstName;
    private String lastName;
}
