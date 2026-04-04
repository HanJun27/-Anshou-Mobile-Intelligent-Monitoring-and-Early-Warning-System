package com.livewell

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.livewell.untils.PrefsManager

/**
 * 密码书查看界面（全屏 Activity）
 */
class PasswordBookViewActivity : BaseActivity() {

    private lateinit var prefsManager: PrefsManager
    
    // 视图引用
    private lateinit var tvPasswordQuestion: TextView
    private lateinit var etAnswerInput: TextInputEditText
    private lateinit var btnVerifyAnswer: MaterialButton
    private lateinit var layoutContentDisplay: View
    private lateinit var tvPasswordContent: TextView
    private lateinit var tvAttachmentsTitle: TextView
    private lateinit var rvAttachments: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_book_view)
        
        prefsManager = PrefsManager(this)
        
        initViews()
        loadQuestion()
        setupListeners()
    }
    
    private fun initViews() {
        tvPasswordQuestion = findViewById(R.id.tvPasswordQuestion)
        etAnswerInput = findViewById(R.id.etAnswerInput)
        btnVerifyAnswer = findViewById(R.id.btnVerifyAnswer)
        layoutContentDisplay = findViewById(R.id.layoutContentDisplay)
        tvPasswordContent = findViewById(R.id.tvPasswordContent)
        tvAttachmentsTitle = findViewById(R.id.tvAttachmentsTitle)
        rvAttachments = findViewById(R.id.rvAttachments)
    }
    
    private fun loadQuestion() {
        val question = prefsManager.getPasswordBookQuestion()
        tvPasswordQuestion.text = if (question.isEmpty()) "问题未设置" else question
    }
    
    private fun setupListeners() {
        // 验证答案按钮
        btnVerifyAnswer.setOnClickListener {
            verifyAnswer()
        }
    }
    
    private fun verifyAnswer() {
        val inputAnswer = etAnswerInput.text.toString()
        val correctAnswer = prefsManager.getPasswordBookAnswer()
        
        if (inputAnswer == correctAnswer) {
            // 答案正确，显示内容
            layoutContentDisplay.visibility = View.VISIBLE
            tvPasswordContent.text = prefsManager.getPasswordBookContent()
            
            // 加载附件
            val attachments = prefsManager.getPasswordBookAttachments()
            if (attachments.isNotEmpty()) {
                tvAttachmentsTitle.visibility = View.VISIBLE
                setupAttachmentRecyclerView(attachments)
            } else {
                tvAttachmentsTitle.visibility = View.GONE
            }
            
            Toast.makeText(this, "验证成功", Toast.LENGTH_SHORT).show()
        } else {
            // 答案错误
            Toast.makeText(this, "答案错误，请重试", Toast.LENGTH_SHORT).show()
            etAnswerInput.text?.clear()
        }
    }
    
    private fun setupAttachmentRecyclerView(attachments: List<String>) {
        rvAttachments.layoutManager = LinearLayoutManager(this)
        val adapter = AttachmentAdapter(attachments, false, null) { path ->
            // 查看附件
            viewAttachment(path)
        }
        rvAttachments.adapter = adapter
    }
    
    private fun viewAttachment(path: String) {
        // 判断文件类型
        if (isImageFile(path)) {
            // 图片：使用应用内查看器
            ImageViewerActivity.start(this, path)
        } else {
            // 其他文件：调用系统查看器
            try {
                val uri = android.net.Uri.parse(path)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(path))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开文件：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".jpg") || 
               lowerPath.endsWith(".jpeg") || 
               lowerPath.endsWith(".png") || 
               lowerPath.endsWith(".gif") || 
               lowerPath.endsWith(".webp") || 
               lowerPath.endsWith(".bmp")
    }
    
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".3gp") -> "video/3gpp"
            else -> "*/*"
        }
    }
}
