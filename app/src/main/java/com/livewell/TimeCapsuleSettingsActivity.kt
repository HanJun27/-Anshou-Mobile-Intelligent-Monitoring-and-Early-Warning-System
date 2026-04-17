package com.livewell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log  // ✅ 新增
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.livewell.untils.PrefsManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 时光胶囊设置界面（全屏 Activity）
 */
class TimeCapsuleSettingsActivity : BaseActivity() {

    private lateinit var prefsManager: PrefsManager
    
    // 视图引用
    private lateinit var switchTimeCapsule: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardEmergencyEmail: MaterialCardView
    private lateinit var cardPasswordBook: MaterialCardView
    private lateinit var cardPasswordVerification: MaterialCardView  // ✅ 新增：密码验证容器
    private lateinit var etAccessPassword: TextInputEditText  // ✅ 新增：访问密码输入框
    private lateinit var btnVerifyPassword: MaterialButton  // ✅ 新增：验证按钮
    private lateinit var switchEmergencyEmail: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchPasswordBook: androidx.appcompat.widget.SwitchCompat
    
    // ✅ 新增：标记是否已通过密码验证
    private var isPasswordVerified = false
    private lateinit var etInactiveThreshold: TextInputEditText
    private lateinit var etEmergencyEmailText: TextInputEditText
    private lateinit var etPasswordQuestion: TextInputEditText
    private lateinit var etPasswordAnswer: TextInputEditText
    private lateinit var etPasswordContent: TextInputEditText
    private lateinit var layoutAttachments: LinearLayout
    private lateinit var btnAddAttachment: MaterialButton
    private lateinit var rvAttachments: RecyclerView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    
    // ✅ 附件相关
    private val attachmentsList = mutableListOf<Attachment>()  // 附件列表
    private var currentPhotoPath: String? = null  // 当前拍照图片路径
    private var currentVideoPath: String? = null  // 当前拍摄视频路径
    
    data class Attachment(val path: String, val type: String, val name: String)  // ✅ 附件数据类
    
    // ✅ 图片拍照结果处理器
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                addAttachment(path, "image", "拍照_${System.currentTimeMillis()}.jpg")
                Toast.makeText(this, "照片已添加", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "拍照取消", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ✅ 视频拍摄结果处理器
    private val captureVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            currentVideoPath?.let { path ->
                addAttachment(path, "video", "视频_${System.currentTimeMillis()}.mp4")
                Toast.makeText(this, "视频已添加", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "拍摄取消", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ✅ 图片选择结果处理器
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            saveUriToAttachments(it, "image")
        }
    }
    
    // ✅ 视频选择结果处理器
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            saveUriToAttachments(it, "video")
        }
    }
    
    // ✅ 权限请求结果处理器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予，执行对应操作
            when (permissionType) {
                PERMISSION_CAMERA_PHOTO -> takePhoto()
                PERMISSION_CAMERA_VIDEO -> captureVideo()
            }
        } else {
            Toast.makeText(this, "需要相机权限才能继续", Toast.LENGTH_SHORT).show()
        }
    }
    
    private var permissionType = 0
    companion object {
        const val TAG = "TimeCapsuleSettings"
        const val PERMISSION_CAMERA_PHOTO = 1
        const val PERMISSION_CAMERA_VIDEO = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_capsule_settings)
        
        prefsManager = PrefsManager(this)
        
        Log.d(TAG, "onCreate - 开始初始化时光胶囊设置界面")
        
        initViews()
        Log.d(TAG, "onCreate - 视图初始化完成")
        
        loadSettings()
        Log.d(TAG, "onCreate - 设置加载完成")
        
        setupListeners()
        Log.d(TAG, "onCreate - 监听器设置完成")
    }
    
    private fun initViews() {
        switchTimeCapsule = findViewById(R.id.switchTimeCapsule)
        cardEmergencyEmail = findViewById(R.id.cardEmergencyEmail)
        cardPasswordBook = findViewById(R.id.cardPasswordBook)
        cardPasswordVerification = findViewById(R.id.cardPasswordVerification)  // ✅ 新增
        etAccessPassword = findViewById(R.id.etAccessPassword)  // ✅ 新增
        btnVerifyPassword = findViewById(R.id.btnVerifyPassword)  // ✅ 新增
        switchEmergencyEmail = findViewById(R.id.switchEmergencyEmail)
        switchPasswordBook = findViewById(R.id.switchPasswordBook)
        etInactiveThreshold = findViewById(R.id.etInactiveThreshold)
        etEmergencyEmailText = findViewById(R.id.etEmergencyEmailText)
        etPasswordQuestion = findViewById(R.id.etPasswordQuestion)
        etPasswordAnswer = findViewById(R.id.etPasswordAnswer)
        etPasswordContent = findViewById(R.id.etPasswordContent)
        layoutAttachments = findViewById(R.id.layoutAttachments)
        btnAddAttachment = findViewById(R.id.btnAddAttachment)
        rvAttachments = findViewById(R.id.rvAttachments)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
    }
    
    private fun loadSettings() {
        // ✅ 初始化验证状态
        isPasswordVerified = false
        
        // ✅ 检查是否已设置访问密码
        val accessPassword = prefsManager.getTimeCapsuleAccessPassword()
        
        Log.d(TAG, "时光胶囊设置 - 读取访问密码：[${accessPassword}], 长度：${accessPassword.length}")
        
        if (accessPassword.isNotEmpty()) {
            // 已设置密码，显示密码验证容器，隐藏所有内容
            Log.d(TAG, "显示密码验证界面")
            cardPasswordVerification.visibility = View.VISIBLE
            cardEmergencyEmail.visibility = View.GONE
            cardPasswordBook.visibility = View.GONE
            btnSave.isEnabled = false
            btnCancel.isEnabled = true  // ✅ 取消按钮始终可用
        } else {
            // 未设置密码，直接显示所有内容
            Log.d(TAG, "无需验证，显示所有内容")
            cardPasswordVerification.visibility = View.GONE
            isPasswordVerified = true  // ✅ 标记为已验证
            showAllContent()
        }
        
        // 加载现有设置
        switchTimeCapsule.isChecked = prefsManager.isTimeCapsuleEnabled()
        switchEmergencyEmail.isChecked = prefsManager.isEmergencyEmailEnabled()
        switchPasswordBook.isChecked = prefsManager.isPasswordBookEnabled()
        etInactiveThreshold.setText(prefsManager.getInactiveThresholdDays().toString())
        etEmergencyEmailText.setText(prefsManager.getEmergencyEmailText())
        etPasswordQuestion.setText(prefsManager.getPasswordBookQuestion())
        etPasswordAnswer.setText(prefsManager.getPasswordBookAnswer())
        etPasswordContent.setText(prefsManager.getPasswordBookContent())
        
        // ✅ 加载附件列表
        loadAttachmentsFromPrefs()
        updateAttachmentsList()
        
        // ✅ 调试：如果密码书开启了，确保密码已保存
        if (switchPasswordBook.isChecked) {
            val question = etPasswordQuestion.text.toString().trim()
            val answer = etPasswordAnswer.text.toString().trim()
            
            if (question.isNotEmpty() && answer.isNotEmpty() && accessPassword.isEmpty()) {
                Log.d(TAG, "检测到密码书已开启且有答案，但访问密码为空，自动保存...")
                prefsManager.setTimeCapsuleAccessPassword(answer)
                Log.d(TAG, "已自动保存访问密码：[${answer}]")
                
                // 重新加载以显示验证界面
                cardPasswordVerification.visibility = View.VISIBLE
                cardEmergencyEmail.visibility = View.GONE
                cardPasswordBook.visibility = View.GONE
                btnSave.isEnabled = false
                btnCancel.isEnabled = true  // ✅ 取消按钮始终可用
            }
        }
    }
    
    private fun setupListeners() {
        // 总开关监听
        switchTimeCapsule.setOnCheckedChangeListener { _, isChecked ->
            updateFeatureCardsVisibility(isChecked, switchPasswordBook.isChecked)
        }
        
        // 密码书开关监听
        switchPasswordBook.setOnCheckedChangeListener { _, isChecked ->
            updateFeatureCardsVisibility(switchTimeCapsule.isChecked, isChecked)
        }
        
        // ✅ 密码验证按钮监听
        btnVerifyPassword.setOnClickListener {
            verifyAccessPassword()
        }
        
        // 添加附件按钮
        btnAddAttachment.setOnClickListener {
            showAddAttachmentDialog()
        }
        
        // 保存按钮
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        // 取消按钮
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun updateFeatureCardsVisibility(isEnabled: Boolean, isPasswordBookEnabled: Boolean) {
        // ✅ 只有在密码验证通过后才显示内容
        if (!isPasswordVerified) {
            Log.d(TAG, "未通过密码验证，隐藏所有内容")
            cardEmergencyEmail.visibility = View.GONE
            cardPasswordBook.visibility = View.GONE
            return
        }
        
        // 紧急邮件卡片：时光胶囊开启时显示
        cardEmergencyEmail.visibility = if (isEnabled) View.VISIBLE else View.GONE
        
        // 密码书卡片：时光胶囊开启时显示
        cardPasswordBook.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }
    
    // ✅ 新增：显示所有内容（通过密码验证后）
    private fun showAllContent() {
        // ✅ 确保只有在验证通过后才显示
        if (!isPasswordVerified) {
            Log.w(TAG, "警告：未通过密码验证，拒绝显示内容")
            return
        }
        
        cardEmergencyEmail.visibility = View.VISIBLE
        cardPasswordBook.visibility = View.VISIBLE
        btnSave.isEnabled = true
        btnCancel.isEnabled = true
        
        // 根据开关状态更新卡片显示
        updateFeatureCardsVisibility(switchTimeCapsule.isChecked, switchPasswordBook.isChecked)
    }
    
    // ✅ 新增：验证访问密码
    private fun verifyAccessPassword() {
        val inputPassword = etAccessPassword.text.toString().trim()
        val savedPassword = prefsManager.getTimeCapsuleAccessPassword()
        
        if (inputPassword == savedPassword) {
            // 密码正确，显示所有内容
            Toast.makeText(this, "验证成功", Toast.LENGTH_SHORT).show()
            cardPasswordVerification.visibility = View.GONE
            isPasswordVerified = true  // ✅ 标记为已验证
            showAllContent()
        } else {
            // 密码错误
            Toast.makeText(this, "密码错误，请重试", Toast.LENGTH_SHORT).show()
            etAccessPassword.text?.clear()
        }
    }
    
    private fun showAddAttachmentDialog() {
        val items = arrayOf("拍照", "从相册选择图片", "拍摄视频", "从相册选择视频")
        
        AlertDialog.Builder(this)
            .setTitle("添加附件")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionForPhoto()
                    1 -> pickImageFromGallery()
                    2 -> requestCameraPermissionForVideo()
                    3 -> pickVideoFromGallery()
                }
            }
            .show()
    }
    
    // ✅ 请求相机权限（拍照）
    private fun requestCameraPermissionForPhoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePhoto()
            }
            else -> {
                permissionType = PERMISSION_CAMERA_PHOTO
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    // ✅ 请求相机权限（录像）
    private fun requestCameraPermissionForVideo() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                captureVideo()
            }
            else -> {
                permissionType = PERMISSION_CAMERA_VIDEO
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    // ✅ 拍照
    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath
            takePictureLauncher.launch(Uri.fromFile(photoFile))
        } catch (e: Exception) {
            Toast.makeText(this, "拍照失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ✅ 录像
    private fun captureVideo() {
        try {
            val videoFile = createVideoFile()
            currentVideoPath = videoFile.absolutePath
            captureVideoLauncher.launch(Uri.fromFile(videoFile))
        } catch (e: Exception) {
            Toast.makeText(this, "拍摄失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ✅ 创建图片文件
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    // ✅ 创建视频文件
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("MP4_${timeStamp}_", ".mp4", storageDir)
    }
    
    // ✅ 从相册选择图片
    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }
    
    // ✅ 从相册选择视频
    private fun pickVideoFromGallery() {
        pickVideoLauncher.launch("video/*")
    }
    
    // ✅ 保存 URI 到附件列表
    private fun saveUriToAttachments(uri: Uri, type: String) {
        try {
            // 复制文件到应用目录
            val fileName = "${type}_${System.currentTimeMillis()}"
            val file = when (type) {
                "image" -> File.createTempFile(fileName, ".jpg", getExternalFilesDir(null))
                "video" -> File.createTempFile(fileName, ".mp4", getExternalFilesDir(null))
                else -> throw Exception("未知类型")
            }
            
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            addAttachment(file.absolutePath, type, fileName)
            Toast.makeText(this, "文件已添加", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ✅ 添加附件到列表
    private fun addAttachment(path: String, type: String, name: String) {
        attachmentsList.add(Attachment(path, type, name))
        updateAttachmentsList()
        saveAttachmentsToPrefs()
    }
    
    // ✅ 删除附件
    private fun removeAttachment(position: Int) {
        if (position in attachmentsList.indices) {
            val attachment = attachmentsList[position]
            // 删除文件
            File(attachment.path).delete()
            attachmentsList.removeAt(position)
            updateAttachmentsList()
            saveAttachmentsToPrefs()
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAttachmentsList() {
        // TODO: 实现 RecyclerView 显示
        // 简单实现：在 layoutAttachments 中显示标签
        layoutAttachments.removeAllViews()
        
        attachmentsList.forEachIndexed { index, attachment ->
            val chip = android.widget.TextView(this).apply {
                text = "${attachment.name} (${if (attachment.type == "image") "图片" else "视频"})"
                setPadding(16, 8, 16, 8)
                textSize = 14f
                setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
                setOnClickListener {
                    // 点击查看或删除
                    showAttachmentOptions(index)
                }
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 8)
            }
            
            layoutAttachments.addView(chip, params)
        }
        
        // 更新 RecyclerView（如果有适配器）
        // rvAttachments.adapter?.notifyDataSetChanged()
    }
    
    // ✅ 显示附件选项
    private fun showAttachmentOptions(position: Int) {
        val options = arrayOf("查看", "删除")
        AlertDialog.Builder(this)
            .setTitle("附件操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewAttachment(position)
                    1 -> removeAttachment(position)
                }
            }
            .show()
    }
    
    // ✅ 查看附件
    private fun viewAttachment(position: Int) {
        if (position in attachmentsList.indices) {
            val attachment = attachmentsList[position]
            try {
                val file = File(attachment.path)
                if (!file.exists()) {
                    Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                    return
                }
                
                if (attachment.type == "image") {
                    ImageViewerActivity.start(this, attachment.path)
                } else {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(android.net.Uri.fromFile(file), "video/*")
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "打开失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // ✅ 保存附件到 SharedPreferences
    // ✅ 保存附件到 PrefsManager（统一存储）
    private fun saveAttachmentsToPrefs() {
        // ✅ 修复：使用 PrefsManager 的统一接口，而不是直接写入 SharedPreferences
        val attachmentPaths = attachmentsList.map { it.path }
        prefsManager.setPasswordBookAttachments(attachmentPaths)
        Log.d(TAG, "✅ 附件已保存到 PrefsManager：${attachmentPaths.size}个")
    }
    
    // ✅ 从 PrefsManager 加载附件（统一存储）
    private fun loadAttachmentsFromPrefs() {
        // ✅ 修复：使用 PrefsManager 的统一接口读取
        val attachmentPaths = prefsManager.getPasswordBookAttachments()
        attachmentsList.clear()
        attachmentPaths.forEach { path ->
            // 从路径中提取文件名
            val fileName = path.substringAfterLast("/").substringAfterLast("\\")
            // 根据文件扩展名判断类型
            val type = when {
                fileName.endsWith(".jpg", ignoreCase = true) || 
                fileName.endsWith(".jpeg", ignoreCase = true) || 
                fileName.endsWith(".png", ignoreCase = true) -> "image"
                fileName.endsWith(".mp4", ignoreCase = true) || 
                fileName.endsWith(".avi", ignoreCase = true) -> "video"
                else -> "file"
            }
            attachmentsList.add(Attachment(path, type, fileName))
        }
        Log.d(TAG, "✅ 从 PrefsManager 加载附件：${attachmentsList.size}个")
    }
    
    private fun saveSettings() {
        prefsManager.setTimeCapsuleEnabled(switchTimeCapsule.isChecked)
        prefsManager.setEmergencyEmailEnabled(switchEmergencyEmail.isChecked)
        prefsManager.setPasswordBookEnabled(switchPasswordBook.isChecked)
        
        val thresholdDays = etInactiveThreshold.text.toString()
        if (thresholdDays.isNotEmpty()) {
            prefsManager.setInactiveThresholdDays(thresholdDays.toIntOrNull() ?: 3)
        }
        
        prefsManager.setEmergencyEmailText(etEmergencyEmailText.text.toString())
        prefsManager.setPasswordBookQuestion(etPasswordQuestion.text.toString())
        prefsManager.setPasswordBookAnswer(etPasswordAnswer.text.toString())
        prefsManager.setPasswordBookContent(etPasswordContent.text.toString())
        
        // ✅ 如果密码书开启了且有设置密码（问题/答案），则保存为访问密码
        if (switchPasswordBook.isChecked) {
            val passwordQuestion = etPasswordQuestion.text.toString().trim()
            val passwordAnswer = etPasswordAnswer.text.toString().trim()
            
            Log.d(TAG, "保存设置 - 密码书开关：开启，问题长度：${passwordQuestion.length}，答案长度：${passwordAnswer.length}")
            
            // 如果有设置问题和答案，将答案作为访问密码
            if (passwordQuestion.isNotEmpty() && passwordAnswer.isNotEmpty()) {
                prefsManager.setTimeCapsuleAccessPassword(passwordAnswer)
                Log.d(TAG, "已保存访问密码")
            } else {
                Log.w(TAG, "警告：密码书已开启但未设置完整的问题和答案")
            }
        } else {
            Log.d(TAG, "密码书开关：关闭")
        }
        
        Toast.makeText(this, "时光胶囊设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
