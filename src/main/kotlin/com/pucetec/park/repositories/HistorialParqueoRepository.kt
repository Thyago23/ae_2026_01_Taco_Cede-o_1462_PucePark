package com.pucetec.park.repositories

import com.pucetec.park.entities.HistorialParqueo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface EstadisticasPersonalesProjection {
    fun getTotalHoras(): Double?
    fun getTotalSesiones(): Long
}

interface RankingProjection {
    fun getUsername(): String
    fun getNombreCompleto(): String?
    fun getTotalHoras(): Double
    fun getTotalSesiones(): Long
}

interface HistorialParqueoRepository : JpaRepository<HistorialParqueo, Long> {

    fun findFirstByPuestoIdAndFechaSalidaIsNullOrderByFechaIngresoDesc(puestoId: Long): Optional<HistorialParqueo>
    fun findFirstByUsernameAndFechaSalidaIsNullOrderByFechaIngresoDesc(username: String): Optional<HistorialParqueo>
    fun findByUsernameOrderByFechaIngresoDesc(username: String): List<HistorialParqueo>
    fun findByPuestoIdOrderByFechaIngresoDesc(puestoId: Long): List<HistorialParqueo>
    fun existsByUsernameAndFechaSalidaIsNull(username: String): Boolean

    @Query(value = """
        SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (h.exit_date - h.entry_date)) / 3600.0), 0) as total_horas,
               COUNT(h.id) as total_sesiones
        FROM parking_history h
        WHERE h.exit_date IS NOT NULL
          AND h.username = :username
          AND EXTRACT(YEAR FROM h.entry_date) = :year
          AND EXTRACT(MONTH FROM h.entry_date) = :month
    """, nativeQuery = true)
    fun getEstadisticasPersonales(
        @Param("username") username: String,
        @Param("year") year: Int,
        @Param("month") month: Int
    ): EstadisticasPersonalesProjection

    // El nombre completo vive en el microservicio users-service (patrón sin BD
    // compartida ni joins entre servicios); el ranking usa solo el username.
    @Query(value = """
        SELECT h.username as username,
               COALESCE(MAX(h.display_name), h.username) as nombre_completo,
               COALESCE(SUM(EXTRACT(EPOCH FROM (h.exit_date - h.entry_date)) / 3600.0), 0) as total_horas,
               COUNT(h.id) as total_sesiones
        FROM parking_history h
        WHERE h.exit_date IS NOT NULL
          AND h.username NOT LIKE 'GUARDIA:%'
          AND EXTRACT(YEAR FROM h.entry_date) = :year
          AND EXTRACT(MONTH FROM h.entry_date) = :month
        GROUP BY h.username
        ORDER BY total_horas DESC, total_sesiones DESC, h.username ASC
        LIMIT 20
    """, nativeQuery = true)
    fun getRankingMensual(
        @Param("year") year: Int,
        @Param("month") month: Int
    ): List<RankingProjection>
}
