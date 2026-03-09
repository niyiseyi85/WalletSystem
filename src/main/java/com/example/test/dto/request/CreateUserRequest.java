package com.example.test.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    private String email;
}
