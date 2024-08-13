package com.example.project.models;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.uuid.Generators;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder
@Document(collection = "Login_Data")
public class User {
  @Id
  private String id= Generators.timeBasedGenerator().generate().toString();

  @NotBlank
  @Size(max = 50)
  @Field("email")
  private String username;

  @NotBlank
  @Field("password")
  @Size(max = 120)
  private String password;

  @NotBlank
  @Size(max = 50)
  private String name;

  private String verificationToken;

  private boolean verified;

}
