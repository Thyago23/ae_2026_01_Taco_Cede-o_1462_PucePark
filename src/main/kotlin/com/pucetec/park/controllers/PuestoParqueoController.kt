package com.pucetec.park.controllers

import com.pucetec.park.dto.CreatePuestoParqueoRequest
import com.pucetec.park.dto.ForzarOcupacionRequest
import com.pucetec.park.dto.OcuparPuestoRequest
import com.pucetec.park.dto.PuestoParqueoResponse
import com.pucetec.park.dto.UpdatePuestoParqueoRequest
import com.pucetec.park.services.PuestoParqueoService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/puestos")
class PuestoParqueoController(
    private val puestoParqueoService: PuestoParqueoService
) {
    private val logger = LoggerFactory.getLogger(PuestoParqueoController::class.java)

    @GetMapping
    fun getAllPuestos(): List<PuestoParqueoResponse> {
        logger.info("GET /api/v1/puestos")
        return puestoParqueoService.getAllPuestos()
    }

    @GetMapping("/zona/{zonaId}")
    fun getPuestosByZona(@PathVariable zonaId: Long): List<PuestoParqueoResponse> {
        logger.info("GET /api/v1/puestos/zona/$zonaId")
        return puestoParqueoService.getPuestosByZona(zonaId)
    }

    @GetMapping("/mi-puesto")
    fun getMiPuesto(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<PuestoParqueoResponse> {
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("cognito:username") ?: jwt.subject
        logger.info("GET /api/v1/puestos/mi-puesto - username=$username")
        val puesto = puestoParqueoService.getMiPuestoActivo(username)
        return if (puesto == null) ResponseEntity.noContent().build() else ResponseEntity.ok(puesto)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPuesto(@RequestBody request: CreatePuestoParqueoRequest): PuestoParqueoResponse {
        logger.info("POST /api/v1/puestos - spaceNumber=${request.spaceNumber}")
        return puestoParqueoService.createPuesto(request)
    }

    @PutMapping("/{id}")
    fun updatePuesto(@PathVariable id: Long, @RequestBody request: UpdatePuestoParqueoRequest): PuestoParqueoResponse {
        logger.info("PUT /api/v1/puestos/$id - spaceNumber=${request.spaceNumber}")
        return puestoParqueoService.updatePuesto(id, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePuesto(@PathVariable id: Long) {
        logger.info("DELETE /api/v1/puestos/$id")
        puestoParqueoService.deletePuesto(id)
    }

    @PutMapping("/{id}/ocupar")
    fun ocuparPuesto(
        @PathVariable id: Long,
        @RequestBody(required = false) request: OcuparPuestoRequest?,
        @AuthenticationPrincipal jwt: Jwt
    ): PuestoParqueoResponse {
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("cognito:username") ?: jwt.subject
        val displayName = request?.fullName?.takeIf { it.isNotBlank() }
            ?: jwt.getClaimAsString("name")
        logger.info("PUT /api/v1/puestos/$id/ocupar - username=$username, displayName=$displayName")
        return puestoParqueoService.ocuparPuesto(id, username, displayName)
    }

    @PutMapping("/{id}/liberar")
    fun liberarPuesto(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): PuestoParqueoResponse {
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("cognito:username") ?: jwt.subject
        logger.info("PUT /api/v1/puestos/$id/liberar - username=$username")
        return puestoParqueoService.liberarPuesto(id, username, isGuard = false)
    }

    @PutMapping("/{id}/forzar-liberacion")
    fun forzarLiberacion(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): PuestoParqueoResponse {
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("cognito:username") ?: jwt.subject
        logger.info("PUT /api/v1/puestos/$id/forzar-liberacion - username=$username")
        return puestoParqueoService.liberarPuesto(id, username, isGuard = true)
    }

    @PutMapping("/{id}/forzar-ocupacion")
    fun forzarOcupacion(@PathVariable id: Long, @RequestBody request: ForzarOcupacionRequest, @AuthenticationPrincipal jwt: Jwt): PuestoParqueoResponse {
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("cognito:username") ?: jwt.subject
        logger.info("PUT /api/v1/puestos/$id/forzar-ocupacion - vehiclePlate=${request.vehiclePlate}, username=$username")
        return puestoParqueoService.forzarOcupacion(id, request.vehiclePlate, username)
    }
}
