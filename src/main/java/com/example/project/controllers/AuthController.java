package com.example.project.controllers;

import java.security.SecureRandom;
import java.util.*;

import com.example.project.models.User;
import com.example.project.payload.request.SignupRequest;
import com.example.project.payload.response.MessageResponse;
import com.example.project.repository.UserRepository;
import com.example.project.payload.request.LoginRequest;
import com.example.project.security.services.UserDetailsImpl;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.example.project.payload.response.UserInfoResponse;
import com.example.project.security.jwt.JwtUtils;


@CrossOrigin(origins = "https://master--aashay-jain.netlify.app", maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class AuthController {
  @Autowired
  AuthenticationManager authenticationManager;

  @Autowired
  JwtUtils jwtUtils;

  @Autowired
  UserRepository userRepository;

  @Autowired
  JavaMailSender javaMailSender;

  @Autowired
  PasswordEncoder passwordEncoder;

  private static final SecureRandom secureRandom = new SecureRandom();
  private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

  @PostMapping("/login")
  public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

    User user=userRepository.findByUsername(loginRequest.getUsername()).isPresent()? userRepository.findByUsername(loginRequest.getUsername()).get() : null;
    if(Objects.nonNull(user) && !user.isVerified())
    {
      return new ResponseEntity<>("Account is not verified !", HttpStatus.UNAUTHORIZED);
    }
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

    SecurityContextHolder.getContext().setAuthentication(authentication);

    UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

    ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
    ResponseCookie loggedInCookie = ResponseCookie.from("loggedIn", "true")
            .httpOnly(false) // Makes the cookie accessible via JavaScript
            .path("/")       // Cookie available for the root path
            .sameSite("None") // Consider using "Lax" during local development
            .secure(true)   // Since localhost is not HTTPS, set secure to false
            .maxAge(24*60*60)
            .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
            .header(HttpHeaders.SET_COOKIE,loggedInCookie.toString())
        .body(new UserInfoResponse(userDetails.getId(),
                                   userDetails.getUsername()));
  }

  @PostMapping("/signout")
  public ResponseEntity<?> logoutUser() {
    ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
    ResponseCookie loggedInCookie = ResponseCookie.from("loggedIn", null)
            .httpOnly(false) // This makes the cookie accessible via JavaScript
            .path("/")
            .maxAge(0)
            .sameSite("None")
            .secure(true)
            .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
            .header(HttpHeaders.SET_COOKIE,loggedInCookie.toString())
            .body(new MessageResponse("You've been signed out!"));
  }

  @PostMapping("/signup")
  public ResponseEntity<String> register(@RequestBody SignupRequest signupRequest, HttpServletRequest request)
  {
    try{
      String email= signupRequest.getEmail();
      User existingUser=userRepository.findByUsername(email).isPresent()? userRepository.findByUsername(email).get() : null;
      if(Objects.nonNull(existingUser) )
      {
        return new ResponseEntity<>("User Already Exists", HttpStatus.FORBIDDEN);
      }
      byte[] randomBytes = new byte[24];
      secureRandom.nextBytes(randomBytes);
      String verificationToken=base64Encoder.encodeToString(randomBytes);
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(email);
      helper.setSubject("Account Verification");
      String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
      String url=String.format("%s/auth/signup/verify?email=%s&token=%s", baseUrl, email, verificationToken);
      String content = "Please click the following link to verify your account:<br> <a href=\"" + url + "\"  target=\"_blank\">Verify Account</a>";
      helper.setText(content,true);

      User user=User.builder().username(email)
              .verificationToken(verificationToken)
              .password(passwordEncoder.encode(signupRequest.getPassword()))
              .name(signupRequest.getName())
              .verified(false).build();

      javaMailSender.send(message);
      userRepository.save(user);

      return new ResponseEntity<>("A link has been sent to your email. Please click on the link to verify your account",HttpStatus.OK);
    }
    catch (Exception e){
      System.out.println(e.getMessage());
      return new ResponseEntity<>("An Error occurred. Please try again !",HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  @GetMapping("/signup/verify")
  public ResponseEntity<String> verifyAccount(@RequestParam String email,@RequestParam String token)
  {
    try{
      Optional<User> user=userRepository.findByUsername(email);

      if(user.get().getVerificationToken().equals(token))
      {
        user.get().setVerified(true);
        userRepository.deleteById(user.get().getId());
        userRepository.save(user.get());
      }
      else {
        return new ResponseEntity<>("Validation Failed.Token does not match.Please try again !",HttpStatus.BAD_REQUEST);
      }
      return new ResponseEntity<>("User verified successfully !", HttpStatus.OK);
    }
    catch (Exception e){
      System.out.println(e.getMessage());
      return new ResponseEntity<>("An Error occurred. Please try again !", HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }
}
