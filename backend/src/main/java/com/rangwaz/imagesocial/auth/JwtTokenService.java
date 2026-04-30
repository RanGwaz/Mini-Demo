package com.rangwaz.imagesocial.auth;

import com.rangwaz.imagesocial.config.JwtProperties;
import com.rangwaz.imagesocial.domain.entity.User;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.accessTokenExpireSeconds()))
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
    }
}
