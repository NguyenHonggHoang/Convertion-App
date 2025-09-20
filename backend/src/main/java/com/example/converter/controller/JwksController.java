package com.example.converter.controller;

import com.example.converter.config.JwtProperties;
import com.example.converter.security.jwt.PemUtil;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final JwtProperties props;
    private final PemUtil pemUtil;

    public JwksController(JwtProperties props, PemUtil pemUtil) {
        this.props = props;
        this.pemUtil = pemUtil;
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        Map<String, PrivateKey> privKeys = pemUtil.parsePrivateKeys(props.getKeys());
        List<JWK> jwks = new ArrayList<>();

        for (Map.Entry<String, PrivateKey> e : privKeys.entrySet()) {
            String kid = e.getKey();
            PrivateKey pk = e.getValue();
            if (pk instanceof RSAPrivateCrtKey rsa) {
                try {
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
                    RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                    RSAKey key = new RSAKey.Builder(pub)
                            .keyUse(KeyUse.SIGNATURE)
                            .algorithm(JWSAlgorithm.RS256)
                            .keyID(kid)
                            .build();
                    jwks.add(key);
                } catch (Exception ex) {
                }
            }
        }

        return new JWKSet(jwks).toJSONObject();
    }
}

