package com.pucetec.park.services

import com.pucetec.park.dto.*
import com.pucetec.park.entities.EstadoPuesto
import com.pucetec.park.entities.HistorialParqueo
import com.pucetec.park.exceptions.*
import com.pucetec.park.mappers.toEntity
import com.pucetec.park.mappers.toResponse
import com.pucetec.park.repositories.HistorialParqueoRepository
import com.pucetec.park.repositories.PuestoParqueoRepository
import com.pucetec.park.repositories.ZonaParqueoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PuestoParqueoService(
    private val puestoParqueoRepository: PuestoParqueoRepository,
    private val zonaParqueoRepository: ZonaParqueoRepository,
    private val historialParqueoRepository: HistorialParqueoRepository,
) {
    private val logger = LoggerFactory.getLogger(PuestoParqueoService::class.java)

    @Transactional(readOnly = true)
    fun getPuestosByZona(zonaId: Long): List<PuestoParqueoResponse> {
        logger.info("Loading parking spaces for zone id=$zonaId...")
        val puestos = puestoParqueoRepository.findByZonaIdOrderByFilaAscOrdenAsc(zonaId)
        logger.info("Found ${puestos.size} parking space(s) in zone id=$zonaId.")
        return puestos.map { it.toResponse() }
    }

    @Transactional
    fun createPuesto(request: CreatePuestoParqueoRequest): PuestoParqueoResponse {
        logger.info("Creating parking space '${request.spaceNumber}' in zone id=${request.zoneId}...")
        if (request.spaceNumber.isBlank()) throw BlankFieldException("spaceNumber must not be blank")
        logger.info("Loading zone id=${request.zoneId}...")
        val zona = zonaParqueoRepository.findById(request.zoneId).orElseThrow {
            ZonaParqueoNotFoundException("Parking zone ${request.zoneId} not found")
        }
        logger.info("Checking for duplicate space number '${request.spaceNumber}' in zone '${zona.nombre}'...")
        if (puestoParqueoRepository.existsByNumeroPuestoAndZonaId(request.spaceNumber, request.zoneId)) {
            throw NumeroPuestoDuplicadoException("Space '${request.spaceNumber}' already exists in zone ${zona.nombre}")
        }
        val count = puestoParqueoRepository.countByZonaId(request.zoneId)
        logger.info("Zone '${zona.nombre}' has $count/${zona.capacidadMaxima} spaces. Checking capacity...")
        if (count >= zona.capacidadMaxima) {
            throw ZonaParqueoLlenaException("Zone ${zona.nombre} has reached its maximum capacity of ${zona.capacidadMaxima} spaces")
        }
        logger.info("Saving parking space '${request.spaceNumber}' to database...")
        val saved = puestoParqueoRepository.save(request.toEntity(zona))
        logger.info("Parking space '${saved.numeroPuesto}' created successfully with id=${saved.id}.")
        return saved.toResponse()
    }

    @Transactional
    fun updatePuesto(id: Long, request: UpdatePuestoParqueoRequest): PuestoParqueoResponse {
        logger.info("Updating parking space id=$id with spaceNumber='${request.spaceNumber}'...")
        val puesto = puestoParqueoRepository.findById(id).orElseThrow {
            PuestoParqueoNotFoundException("Parking space $id not found")
        }
        logger.info("Parking space id=$id found: '${puesto.numeroPuesto}'. Validating new data...")
        if (request.spaceNumber.isBlank()) throw BlankFieldException("spaceNumber must not be blank")
        if (puestoParqueoRepository.existsByNumeroPuestoAndZonaIdAndIdNot(request.spaceNumber, puesto.zona!!.id, id)) {
            throw NumeroPuestoDuplicadoException("Space '${request.spaceNumber}' already exists in zone ${puesto.zona!!.nombre}")
        }
        puesto.numeroPuesto = request.spaceNumber
        logger.info("Saving updated parking space id=$id...")
        val saved = puestoParqueoRepository.save(puesto)
        logger.info("Parking space id=$id updated successfully.")
        return saved.toResponse()
    }

    @Transactional
    fun deletePuesto(id: Long) {
        logger.info("Deleting parking space id=$id...")
        val puesto = puestoParqueoRepository.findById(id).orElseThrow {
            PuestoParqueoNotFoundException("Parking space $id not found")
        }
        logger.info("Parking space id=$id found: '${puesto.numeroPuesto}'. Checking status...")
        if (puesto.estado == EstadoPuesto.OCUPADO) {
            logger.warn("Parking space id=$id is occupied and cannot be deleted.")
            throw SlotAlreadyOccupiedException("Cannot delete parking space $id because it is occupied. Free it first.")
        }
        val historial = historialParqueoRepository.findByPuestoIdOrderByFechaIngresoDesc(id)
        if (historial.isNotEmpty()) {
            logger.info("Removing ${historial.size} history entry(ies) referencing space id=$id...")
            historialParqueoRepository.deleteAll(historial)
        }
        puestoParqueoRepository.delete(puesto)
        logger.info("Parking space id=$id deleted successfully.")
    }

    @Transactional
    fun getAllPuestos(): List<PuestoParqueoResponse> {
        logger.info("Loading all parking spaces...")
        val puestos = puestoParqueoRepository.findAll()
        logger.info("Found ${puestos.size} total parking space(s).")
        return puestos.map { it.toResponse() }
    }

    @Transactional
    fun ocuparPuesto(id: Long, username: String, displayName: String? = null): PuestoParqueoResponse {
        logger.info("User '$username' is requesting to occupy parking space id=$id...")
        // El perfil vive en el microservicio 'users-service' (patrón sin llamadas entre servicios).
        // El cliente (iOS) obliga a completar el perfil en el onboarding antes de permitir ocupar.
        logger.info("Checking if user '$username' already has an active space...")
        if (historialParqueoRepository.existsByUsernameAndFechaSalidaIsNull(username)) {
            logger.warn("User '$username' already has an active parking space. Cannot occupy another.")
            throw UserAlreadyOccupyingException("You already have an active space. Release it before occupying another.")
        }
        logger.info("Acquiring pessimistic lock on parking space id=$id...")
        val puesto = puestoParqueoRepository.findByIdWithPessimisticLock(id).orElseThrow {
            PuestoParqueoNotFoundException("Parking space $id not found")
        }
        if (puesto.estado == EstadoPuesto.OCUPADO) {
            logger.warn("Parking space id=$id is already occupied.")
            throw SlotAlreadyOccupiedException("Parking space $id is already occupied")
        }
        puesto.estado = EstadoPuesto.OCUPADO
        puestoParqueoRepository.save(puesto)
        val codigoTicket = "PARK-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        logger.info("Saving parking history entry for user '$username', space id=$id, ticket='$codigoTicket'...")
        historialParqueoRepository.save(HistorialParqueo(puesto = puesto, username = username, codigoTicket = codigoTicket, nombreMostrar = displayName))
        logger.info("Parking space id=$id successfully occupied by '$username'. Ticket: $codigoTicket.")
        return puesto.toResponse()
    }

    @Transactional
    fun forzarOcupacion(id: Long, vehiclePlate: String, guardUsername: String): PuestoParqueoResponse {
        logger.info("Guard '$guardUsername' is force-occupying parking space id=$id for vehicle '$vehiclePlate'...")
        if (vehiclePlate.isBlank()) throw BlankFieldException("vehiclePlate must not be blank")
        logger.info("Acquiring pessimistic lock on parking space id=$id...")
        val puesto = puestoParqueoRepository.findByIdWithPessimisticLock(id).orElseThrow {
            PuestoParqueoNotFoundException("Parking space $id not found")
        }
        if (puesto.estado == EstadoPuesto.OCUPADO) {
            logger.warn("Parking space id=$id is already occupied. Force-occupation by guard '$guardUsername' rejected.")
            throw SlotAlreadyOccupiedException("Parking space $id is already occupied")
        }
        puesto.estado = EstadoPuesto.OCUPADO
        puestoParqueoRepository.save(puesto)
        val codigoTicket = "G-${UUID.randomUUID().toString().take(10).uppercase()}"
        logger.info("Saving guard history entry: guard='GUARDIA:$guardUsername', space=$id, plate='$vehiclePlate', ticket='$codigoTicket'...")
        historialParqueoRepository.save(
            HistorialParqueo(puesto = puesto, username = "GUARDIA:$guardUsername", codigoTicket = codigoTicket, placaVehiculo = vehiclePlate)
        )
        logger.info("Parking space id=$id successfully force-occupied by guard '$guardUsername'. Ticket: $codigoTicket.")
        return puesto.toResponse()
    }

    @Transactional
    fun liberarPuesto(id: Long, username: String, isGuard: Boolean): PuestoParqueoResponse {
        logger.info("${if (isGuard) "Guard" else "User"} '$username' is requesting to free parking space id=$id...")
        logger.info("Acquiring pessimistic lock on parking space id=$id...")
        val puesto = puestoParqueoRepository.findByIdWithPessimisticLock(id).orElseThrow {
            PuestoParqueoNotFoundException("Parking space $id not found")
        }
        if (puesto.estado == EstadoPuesto.DISPONIBLE) {
            logger.warn("Parking space id=$id is already available. Cannot free it again.")
            throw SlotAlreadyAvailableException("Parking space $id is already available")
        }
        logger.info("Loading active history entry for parking space id=$id...")
        val historial = historialParqueoRepository.findFirstByPuestoIdAndFechaSalidaIsNullOrderByFechaIngresoDesc(id)
            .orElseThrow { HistorialParqueoNotFoundException("No active history found for space $id") }
        if (!isGuard && historial.username != username) {
            logger.warn("Unauthorized attempt: user '$username' tried to free space id=$id owned by '${historial.username}'.")
            throw UnauthorizedAccessException("User $username is not authorized to free this space")
        }
        historial.fechaSalida = LocalDateTime.now()
        historialParqueoRepository.save(historial)
        puesto.estado = EstadoPuesto.DISPONIBLE
        logger.info("Parking space id=$id successfully freed by '${if (isGuard) "guard" else "user"} $username'. Exit recorded at ${historial.fechaSalida}.")
        return puestoParqueoRepository.save(puesto).toResponse()
    }
}
