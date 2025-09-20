package com.example.converter.security.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.example.converter.config.JwtProperties;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

// Use spring security oauth2 resource server
@Deprecated
@Component
public class JwtValidator {
  private final JwtProperties props;
  private final JwksService jwks;

  public JwtValidator(JwtProperties props, JwksService jwks) {
    this.props = props; this.jwks = jwks;
  }

  public JWTClaimsSet validate(String token) throws Exception {
    SignedJWT jwt = SignedJWT.parse(token);
    JWSHeader h = jwt.getHeader();
    String kid = h.getKeyID();
    if (kid == null) throw new JOSEException("Missing kid");

    JWK jwk = jwks.selectByKid(kid);
    if (jwk == null) throw new JOSEException("Unknown kid: " + kid);

    JWSVerifier verifier;
    if (jwk instanceof RSAKey rsa) {
      verifier = new RSASSAVerifier(rsa.toRSAPublicKey());
    } else if (jwk instanceof ECKey ec) {
      verifier = new ECDSAVerifier(ec.toECPublicKey());
    } else if (jwk instanceof OctetSequenceKey oct) {
      verifier = new MACVerifier(oct.toByteArray());
    } else {
      throw new JOSEException("Unsupported key type");
    }

    if (!jwt.verify(verifier)) throw new JOSEException("Invalid signature");
    JWTClaimsSet claims = jwt.getJWTClaimsSet();


    long now = Instant.now().getEpochSecond();
    long leeway = props.getLeewaySeconds();
    Date exp = claims.getExpirationTime();
    Date nbf = claims.getNotBeforeTime();
    if (exp != null && exp.toInstant().getEpochSecond() < now - leeway) throw new JOSEException("Token expired");
    if (nbf != null && nbf.toInstant().getEpochSecond() - leeway > now) throw new JOSEException("Token not active");

    // iss / aud
    if (props.getIssuers()!=null && !props.getIssuers().isEmpty()) {
      if (claims.getIssuer()==null || !props.getIssuers().contains(claims.getIssuer())) throw new JOSEException("Bad issuer");
    }
    if (props.getAudiences()!=null && !props.getAudiences().isEmpty()) {
      List<String> aud = claims.getAudience();
      if (aud==null || Collections.disjoint(aud, props.getAudiences())) throw new JOSEException("Bad audience");
    }
    return claims;
  }
}
