package com.livewell.controller

import com.livewell.dto.*
import com.livewell.entity.User
import com.livewell.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/users")
class UserController(
    private val userRepository: UserRepository
) {
    
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserDTO>> {
        val users = userRepository.findAll()
            .map { user -> user.toDTO() }
        return ResponseEntity.ok(users)
    }
    
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserDTO> {
        val user = userRepository.findById(id)
            .orElseThrow { RuntimeException("用户不存在") }
        return ResponseEntity.ok(user.toDTO())
    }
    
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserDTO> {
        if (userRepository.existsByUsername(request.username)) {
            throw RuntimeException("用户名已存在")
        }
        
        val user = User(
            username = request.username,
            password = request.password, // TODO: 使用 BCrypt 加密
            name = request.name,
            phone = request.phone,
            email = request.email,
            avatarColor = request.avatarColor
        )
        
        val savedUser = userRepository.save(user)
        return ResponseEntity.ok(savedUser.toDTO())
    }
    
    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserDTO> {
        val user = userRepository.findById(id)
            .orElseThrow { RuntimeException("用户不存在") }
        
        // TODO: 需要添加可变字段的支持
        
        return ResponseEntity.ok(user.toDTO())
    }
    
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val user = userRepository.findById(id)
            .orElseThrow { RuntimeException("用户不存在") }
        
        userRepository.delete(user)
        return ResponseEntity.ok(mapOf("message" to "删除成功"))
    }
    
    private fun User.toDTO(): UserDTO {
        return UserDTO(
            id = this.id,
            username = this.username,
            name = this.name,
            phone = this.phone,
            email = this.email,
            avatarColor = this.avatarColor,
            status = this.status.name
        )
    }
}