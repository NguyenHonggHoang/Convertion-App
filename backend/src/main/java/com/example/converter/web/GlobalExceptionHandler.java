package com.example.converter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.*;

@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String,Object>> handleInvalid(MethodArgumentNotValidException ex){
    List<Map<String,String>> details = new ArrayList<>();
    ex.getBindingResult().getFieldErrors().forEach(err -> {
      Map<String,String> d = new HashMap<>();
      d.put("field", err.getField());
      d.put("message", err.getDefaultMessage());
      d.put("code", err.getCode());
      details.add(d);
    });
    Map<String,Object> body = new HashMap<>();
    body.put("error","validation_error");
    body.put("details", details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String,Object>> handleViolation(ConstraintViolationException ex){
    List<Map<String,String>> details = new ArrayList<>();
    ex.getConstraintViolations().forEach(v -> {
      Map<String,String> d = new HashMap<>();
      d.put("field", String.valueOf(v.getPropertyPath()));
      d.put("message", v.getMessage());
      d.put("code", v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
      details.add(d);
    });
    Map<String,Object> body = new HashMap<>();
    body.put("error","validation_error");
    body.put("details", details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String,Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex){
    Map<String,Object> body = new HashMap<>();
    body.put("error","validation_error");
    body.put("details", List.of(Map.of("field", ex.getName(), "message", "type mismatch", "code", "TypeMismatch")));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
}
