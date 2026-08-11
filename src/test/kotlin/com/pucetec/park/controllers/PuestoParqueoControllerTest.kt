package com.pucetec.park.controllers

import com.pucetec.park.config.SecurityConfig
import com.pucetec.park.dto.PuestoParqueoResponse
import com.pucetec.park.dto.ZonaParqueoResponse
import com.pucetec.park.entities.EstadoPuesto
import com.pucetec.park.exceptions.*
import com.pucetec.park.services.PuestoParqueoService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PuestoParqueoController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class PuestoParqueoControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var puestoParqueoService: PuestoParqueoService
    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val zonaResponse = ZonaParqueoResponse(
        id = 1L, name = "Zona A", description = "", location = "", maxCapacity = 10,
        availableSpaces = 9, occupiedSpaces = 1, totalSpaces = 10
    )
    private val puestoResponse = PuestoParqueoResponse(
        id = 1L, spaceNumber = "A01", row = "A", order = 1,
        status = EstadoPuesto.DISPONIBLE, zone = zonaResponse
    )
    private val puestoOcupadoResponse = puestoResponse.copy(status = EstadoPuesto.OCUPADO)

    private val createBody = """{"zoneId":1,"spaceNumber":"A01","row":"A","order":1}"""
    private val updateBody = """{"spaceNumber":"A01-MOD"}"""

    @Test
    fun `GET puestos por zona con token responde 200`() {
        whenever(puestoParqueoService.getPuestosByZona(1L)).thenReturn(listOf(puestoResponse))

        mockMvc.perform(get("/api/v1/puestos/zona/1").with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].spaceNumber").value("A01"))
            .andExpect(jsonPath("$[0].status").value("DISPONIBLE"))
    }

    @Test
    fun `GET puestos por zona sin token responde 401`() {
        mockMvc.perform(get("/api/v1/puestos/zona/1"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST puestos sin token responde 401`() {
        mockMvc.perform(
            post("/api/v1/puestos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST puestos con rol USER responde 403`() {
        mockMvc.perform(
            post("/api/v1/puestos")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST puestos con rol ADMIN responde 201`() {
        whenever(puestoParqueoService.createPuesto(any())).thenReturn(puestoResponse)

        mockMvc.perform(
            post("/api/v1/puestos")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.spaceNumber").value("A01"))
    }

    @Test
    fun `POST puestos responde 409 cuando la zona esta llena`() {
        whenever(puestoParqueoService.createPuesto(any()))
            .thenAnswer { throw ZonaParqueoLlenaException("Zona llena") }

        mockMvc.perform(
            post("/api/v1/puestos")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"zoneId":1,"spaceNumber":"A11","row":"B","order":11}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `PUT puestos update sin token responde 401`() {
        mockMvc.perform(
            put("/api/v1/puestos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT puestos update con rol USER responde 403`() {
        mockMvc.perform(
            put("/api/v1/puestos/1")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `PUT puestos update con rol ADMIN responde 200`() {
        whenever(puestoParqueoService.updatePuesto(any(), any())).thenReturn(puestoResponse)

        mockMvc.perform(
            put("/api/v1/puestos/1")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        ).andExpect(status().isOk)
    }

    @Test
    fun `GET mi-puesto con puesto activo responde 200`() {
        whenever(puestoParqueoService.getMiPuestoActivo(any())).thenReturn(puestoOcupadoResponse)

        mockMvc.perform(get("/api/v1/puestos/mi-puesto").with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OCUPADO"))
    }

    @Test
    fun `GET mi-puesto sin puesto activo responde 204`() {
        whenever(puestoParqueoService.getMiPuestoActivo(any())).thenReturn(null)

        mockMvc.perform(get("/api/v1/puestos/mi-puesto").with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `GET mi-puesto sin token responde 401`() {
        mockMvc.perform(get("/api/v1/puestos/mi-puesto"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `DELETE puestos sin token responde 401`() {
        mockMvc.perform(delete("/api/v1/puestos/1"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `DELETE puestos con rol USER responde 403`() {
        mockMvc.perform(
            delete("/api/v1/puestos/1")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE puestos con rol ADMIN responde 204`() {
        mockMvc.perform(
            delete("/api/v1/puestos/1")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE puestos responde 409 cuando el puesto esta ocupado`() {
        whenever(puestoParqueoService.deletePuesto(any()))
            .thenAnswer { throw SlotAlreadyOccupiedException("Puesto ocupado") }

        mockMvc.perform(
            delete("/api/v1/puestos/1")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `PUT ocupar sin token responde 401`() {
        mockMvc.perform(put("/api/v1/puestos/1/ocupar"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT ocupar con rol ADMIN responde 200`() {
        whenever(puestoParqueoService.ocuparPuesto(any(), any(), anyOrNull())).thenReturn(puestoOcupadoResponse)

        mockMvc.perform(
            put("/api/v1/puestos/1/ocupar")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        ).andExpect(status().isOk)
    }

    @Test
    fun `PUT ocupar con rol USER responde 200`() {
        whenever(puestoParqueoService.ocuparPuesto(any(), any(), anyOrNull())).thenReturn(puestoOcupadoResponse)

        mockMvc.perform(
            put("/api/v1/puestos/1/ocupar")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OCUPADO"))
    }

    @Test
    fun `PUT ocupar responde 409 cuando puesto ya esta ocupado`() {
        whenever(puestoParqueoService.ocuparPuesto(any(), any(), anyOrNull()))
            .thenAnswer { throw SlotAlreadyOccupiedException("Puesto ya ocupado") }

        mockMvc.perform(
            put("/api/v1/puestos/1/ocupar")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `PUT liberar sin token responde 401`() {
        mockMvc.perform(put("/api/v1/puestos/1/liberar"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT liberar con rol USER responde 200`() {
        whenever(puestoParqueoService.liberarPuesto(any(), any(), any())).thenReturn(puestoResponse)

        mockMvc.perform(
            put("/api/v1/puestos/1/liberar")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isOk)
    }

    @Test
    fun `PUT liberar responde 409 cuando puesto ya esta disponible`() {
        whenever(puestoParqueoService.liberarPuesto(any(), any(), any()))
            .thenAnswer { throw SlotAlreadyAvailableException("Puesto ya disponible") }

        mockMvc.perform(
            put("/api/v1/puestos/1/liberar")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `PUT forzar-liberacion sin token responde 401`() {
        mockMvc.perform(put("/api/v1/puestos/1/forzar-liberacion"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT forzar-liberacion con rol USER responde 403`() {
        mockMvc.perform(
            put("/api/v1/puestos/1/forzar-liberacion")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER")))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `PUT forzar-liberacion con rol GUARD responde 200`() {
        whenever(puestoParqueoService.liberarPuesto(any(), any(), any())).thenReturn(puestoResponse)

        mockMvc.perform(
            put("/api/v1/puestos/1/forzar-liberacion")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_GUARD")))
        ).andExpect(status().isOk)
    }
}
