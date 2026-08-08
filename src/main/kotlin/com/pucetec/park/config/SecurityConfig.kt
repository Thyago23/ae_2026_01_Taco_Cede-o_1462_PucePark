package com.pucetec.park.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Health check público (para el healthcheck de Docker / balanceador)
                auth.requestMatchers("/actuator/health").permitAll()
                // Zonas
                auth.requestMatchers(HttpMethod.GET, "/api/v1/zonas", "/api/v1/zonas/*/estadisticas").hasAnyRole("ADMIN", "GUARD", "USER")
                auth.requestMatchers(HttpMethod.POST, "/api/v1/zonas").hasRole("ADMIN")
                auth.requestMatchers(HttpMethod.PUT, "/api/v1/zonas/*").hasRole("ADMIN")
                auth.requestMatchers(HttpMethod.DELETE, "/api/v1/zonas/*").hasRole("ADMIN")
                // Puestos — acciones de estado primero (más específicas)
                auth.requestMatchers(HttpMethod.PUT, "/api/v1/puestos/*/forzar-liberacion", "/api/v1/puestos/*/forzar-ocupacion").hasAnyRole("ADMIN", "GUARD")
                auth.requestMatchers(HttpMethod.PUT, "/api/v1/puestos/*/ocupar", "/api/v1/puestos/*/liberar").hasAnyRole("ADMIN", "GUARD", "USER")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/puestos", "/api/v1/puestos/zona/*").hasAnyRole("ADMIN", "GUARD", "USER")
                auth.requestMatchers(HttpMethod.POST, "/api/v1/puestos").hasRole("ADMIN")
                auth.requestMatchers(HttpMethod.PUT, "/api/v1/puestos/*").hasRole("ADMIN")
                auth.requestMatchers(HttpMethod.DELETE, "/api/v1/puestos/*").hasRole("ADMIN")
                // Historial y ranking
                auth.requestMatchers(HttpMethod.GET, "/api/v1/historial/me", "/api/v1/historial/me/estadisticas").hasAnyRole("ADMIN", "USER")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/historial/guardia/me").hasAnyRole("ADMIN", "GUARD")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/historial/ranking/mensual").hasAnyRole("ADMIN", "GUARD", "USER")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/historial/puesto/*").hasAnyRole("ADMIN", "GUARD")
                // El perfil de usuario vive en el microservicio 'users-service' (/users/me)
                auth.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(cognitoGroupsConverter()) }
            }
        return http.build()
    }

    @Bean
    fun cognitoGroupsConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val groups = jwt.getClaimAsStringList("cognito:groups") ?: emptyList()
            groups.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") }
        }
        return converter
    }
}
