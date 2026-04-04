package com.livewell.controller

import com.livewell.dto.*
import com.livewell.entity.CommunityDailyReport
import com.livewell.repository.CommunityDailyReportRepository
import com.livewell.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/community-reports")
class CommunityReportController(
    private val repository: CommunityDailyReportRepository,
    private val userRepository: UserRepository
) {
    
    @GetMapping
    fun getAllReports(): ResponseEntity<List<CommunityDailyReportDTO>> {
        val reports = repository.findAll()
            .map { it.toDTO() }
        return ResponseEntity.ok(reports)
    }
    
    @GetMapping("/user/{userId}")
    fun getUserReports(
        @PathVariable userId: Long,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<List<CommunityDailyReportDTO>> {
        val reports = if (startDate != null && endDate != null) {
            repository.findByUserIdAndReportDateBetween(userId, startDate, endDate)
        } else {
            repository.findByUserId(userId)
        }.map { it.toDTO() }
        
        return ResponseEntity.ok(reports)
    }
    
    @GetMapping("/today")
    fun getTodayReport(@RequestParam userId: Long): ResponseEntity<CommunityDailyReportDTO?> {
        val report = repository.findByUserIdAndReportDate(userId, LocalDate.now())
        return ResponseEntity.ok(report?.toDTO())
    }
    
    @PostMapping
    fun createReport(
        @RequestParam userId: Long,
        @RequestBody request: CreateCommunityReportRequest
    ): ResponseEntity<CommunityDailyReportDTO> {
        val user = userRepository.findById(userId)
            .orElseThrow { RuntimeException("用户不存在") }
        
        val report = CommunityDailyReport(
            user = user,
            reportDate = LocalDate.now(),
            stepCount = request.stepCount,
            sleepDuration = request.sleepDuration,
            sleepQuality = request.sleepQuality,
            screenOnTime = request.screenOnTime,
            appUsageTime = request.appUsageTime,
            checkinStatus = request.checkinStatus,
            batteryLevel = request.batteryLevel,
            isCharging = request.isCharging,
            networkType = request.networkType,
            calorieBurn = request.calorieBurn,
            unlockCount = request.unlockCount
        )
        
        val savedReport = repository.save(report)
        return ResponseEntity.ok(savedReport.toDTO())
    }
    
    private fun CommunityDailyReport.toDTO(): CommunityDailyReportDTO {
        return CommunityDailyReportDTO(
            id = this.id,
            userId = this.user.id,
            reportDate = this.reportDate,
            stepCount = this.stepCount,
            sleepDuration = this.sleepDuration,
            sleepQuality = this.sleepQuality,
            screenOnTime = this.screenOnTime,
            appUsageTime = this.appUsageTime,
            checkinStatus = this.checkinStatus,
            batteryLevel = this.batteryLevel,
            createdAt = this.createdAt.toLocalDate()
        )
    }
}