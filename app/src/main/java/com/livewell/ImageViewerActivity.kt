package com.livewell

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

/**
 * 应用内图片查看器（支持放大、缩放）
 */
class ImageViewerActivity : AppCompatActivity() {
    
    private lateinit var photoView: PhotoView
    private lateinit var ivClose: ImageView
    
    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        
        fun start(activity: AppCompatActivity, imagePath: String) {
            val intent = android.content.Intent(activity, ImageViewerActivity::class.java)
            intent.putExtra(EXTRA_IMAGE_PATH, imagePath)
            activity.startActivity(intent)
            // ✅ 使用系统默认过渡动画
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)
        
        initViews()
        loadImage()
        setupListeners()
    }
    
    private fun initViews() {
        photoView = findViewById(R.id.photoView)
        ivClose = findViewById(R.id.ivClose)
    }
    
    private fun loadImage() {
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH) ?: run {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // 使用 Glide 加载图片，支持 PhotoView 的缩放功能
            Glide.with(this)
                .load(file)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(photoView)
                
        } catch (e: Exception) {
            Toast.makeText(this, "加载图片失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupListeners() {
        // 关闭按钮
        ivClose.setOnClickListener {
            finish()
        }
        
        // 点击图片切换全屏显示/隐藏 UI
        photoView.setOnClickListener {
            toggleUI()
        }
    }
    
    private var isUIVisible = true
    
    private fun toggleUI() {
        isUIVisible = !isUIVisible
        ivClose.isVisible = isUIVisible
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
        // ✅ 使用系统默认过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
