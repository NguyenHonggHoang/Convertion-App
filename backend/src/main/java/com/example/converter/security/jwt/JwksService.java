package com.example.converter.security.jwt;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import java.net.URL;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;
import com.example.converter.config.JwtProperties;
import org.springframework.stereotype.Service;

@Service
public class JwksService {
  private final JwtProperties props;
  private volatile JWKSource<com.nimbusds.jose.proc.SecurityContext> source;
  private volatile long lastInit = 0L;

  public JwksService(JwtProperties props) {
    this.props = props;
  }

  private synchronized void ensure() {
    long now = System.currentTimeMillis();
    if (source == null || (now - lastInit) > TimeUnit.SECONDS.toMillis(props.getJwksRefreshSeconds())) {
      try {
        source = new RemoteJWKSet<>(new URL(props.getJwksUri()));
        lastInit = now;
      } catch (Exception e) {
        throw new IllegalStateException("Cannot init JWKS: " + e.getMessage(), e);
      }
    }
  }

  public JWK selectByKid(String kid) {
    ensure();
    try {
      var selector = new JWKSelector(new JWKMatcher.Builder().keyID(kid).build());
      var jwks = source.get(new JWKSelector(new JWKMatcher.Builder().keyID(kid).build()), null);
      return jwks.isEmpty() ? null : jwks.get(0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
